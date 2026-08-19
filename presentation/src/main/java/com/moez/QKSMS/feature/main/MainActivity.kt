/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of Open Messages.
 *
 * Open Messages is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open Messages is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Messages.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.openmessages.feature.main

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dagger.android.AndroidInjection
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.androidxcompat.drawerOpen
import io.openmessages.common.base.QkThemedActivity
import io.openmessages.common.util.DialogHost
import io.openmessages.common.util.extensions.applyInsetBottomMargin
import io.openmessages.common.util.extensions.applyInsetPadding
import io.openmessages.common.util.extensions.applyInsetTop
import io.openmessages.common.util.extensions.autoScrollToStart
import io.openmessages.common.util.extensions.dismissKeyboard
import io.openmessages.common.util.extensions.resolveThemeColor
import io.openmessages.common.util.extensions.scrapViews
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.setTint
import io.openmessages.common.util.extensions.setVisible
import io.openmessages.common.util.extensions.themeButtons
import io.openmessages.common.widget.TextInputDialog
import io.openmessages.feature.blocking.BlockingDialog
import io.openmessages.databinding.MainActivityBinding
import io.openmessages.databinding.MainPermissionHintBinding
import io.openmessages.databinding.MainSyncingBinding
import io.openmessages.feature.changelog.ChangelogDialog
import io.openmessages.feature.conversations.ConversationItemTouchCallback
import io.openmessages.feature.conversations.ConversationsAdapter
import io.openmessages.manager.ChangelogManager
import io.openmessages.repository.SyncRepository
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

// Peak opacity of the dynamic status-bar scrim once the toolbar is fully collapsed (a subtle veil).
private const val STATUS_BAR_SCRIM_ALPHA = 0.32f

class MainActivity : QkThemedActivity(), MainView {

    @Inject lateinit var blockingDialog: BlockingDialog
    @Inject lateinit var disposables: CompositeDisposable
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var conversationsAdapter: ConversationsAdapter
    @Inject lateinit var drawerBadgesExperiment: DrawerBadgesExperiment
    @Inject lateinit var searchAdapter: SearchAdapter
    @Inject lateinit var itemTouchCallback: ConversationItemTouchCallback
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: MainActivityBinding
    private lateinit var snackbarBinding: MainPermissionHintBinding
    private lateinit var syncingBinding: MainSyncingBinding

    private var searchExpanded = false
    private var lastState: MainState? = null
    private var nextUnreadIndex = 0
    private var scrollToTopVisible = false
    private var statusBarScrimListener: AppBarLayout.OnOffsetChangedListener? = null

    /** What the drawer was last told to do, so that it is only ever told when that changes. */
    private var appliedDrawerOpen: Boolean? = null

    override val onNewIntentIntent: Subject<Intent> = PublishSubject.create()
    override val activityResumedIntent: Subject<Boolean> = PublishSubject.create()
    override val queryChangedIntent by lazy { binding.toolbarSearch.textChanges() }
    override val composeIntent by lazy { binding.compose.clicks() }
    override val drawerToggledIntent: Observable<Boolean> by lazy {
        binding.drawerLayout.drawerOpen(Gravity.START)
    }
    override val homeIntent: Subject<Unit> = PublishSubject.create()
    override val navigationIntent: Observable<NavItem> by lazy {
        Observable.merge(listOf(
                backPressedSubject,
                binding.drawer.inbox.clicks().map { NavItem.INBOX },
                binding.drawer.archived.clicks().map { NavItem.ARCHIVED },
                binding.drawer.backup.clicks().map { NavItem.BACKUP },
                binding.drawer.scheduled.clicks().map { NavItem.SCHEDULED },
                binding.drawer.blocking.clicks().map { NavItem.BLOCKING },
                binding.drawer.messageUtils.clicks().map { NavItem.MESSAGE_UTILS },
                binding.drawer.templates.clicks().map { NavItem.TEMPLATES },
                binding.drawer.settings.clicks().map { NavItem.SETTINGS },
                binding.drawer.about.clicks().map { NavItem.ABOUT },
//                plus.clicks().map { NavItem.PLUS },
//                help.clicks().map { NavItem.HELP },
                binding.drawer.invite.clicks().map { NavItem.INVITE }))
    }
    override val optionsItemIntent: Subject<Int> = PublishSubject.create()
//    override val plusBannerIntent by lazy { plusBanner.clicks() }
    override val dismissRatingIntent by lazy { binding.drawer.rateDismiss.clicks() }
    override val rateIntent by lazy { binding.drawer.rateOkay.clicks() }
    override val conversationsSelectedIntent by lazy { conversationsAdapter.selectionChanges }
    override val confirmDeleteIntent: Subject<List<Long>> = PublishSubject.create()
    override val dialogDismissedIntent: Subject<MainDialog> = PublishSubject.create()
    override val renameConversationIntent: Subject<Pair<Long, String>> = PublishSubject.create()
    override val swipeConversationIntent by lazy { itemTouchCallback.swipes }
    override val changelogMoreIntent by lazy { changelogDialog.moreClicks }
    override val undoArchiveIntent: Subject<Unit> = PublishSubject.create()
    override val snackbarButtonIntent: Subject<Unit> = PublishSubject.create()

    private val viewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory)[MainViewModel::class.java]
    }
    private val toggle by lazy {
        ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.main_drawer_open_cd,
            0
        )
    }
    private val itemTouchHelper by lazy { ItemTouchHelper(itemTouchCallback) }
    private val progressAnimator by lazy {
        ObjectAnimator.ofInt(syncingBinding.syncingProgress, "progress", 0, 0)
    }
    private val changelogDialog by lazy { ChangelogDialog(this) }

    private val dialogHost = DialogHost<MainDialog>(
        build = { spec -> buildDialog(spec).themeButtons(colors.theme().theme) },
        onClosed = dialogDismissedIntent::onNext)

    private val backPressedSubject: Subject<NavItem> = PublishSubject.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarSearch.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
        binding.toolbarSearch.setHintTextColor(resolveThemeColor(android.R.attr.textColorTertiary))
        viewModel.bindView(this)
        onNewIntentIntent.onNext(intent)

        snackbarBinding = MainPermissionHintBinding.bind(binding.snackbar.inflate()).also {
            it.snackbarButton.clicks()
                .autoDisposable(scope(Lifecycle.Event.ON_DESTROY))
                .subscribe(snackbarButtonIntent)
        }

        syncingBinding = MainSyncingBinding.bind(binding.syncing.inflate()).also {
            it.syncingProgress.progressTintList = ColorStateList.valueOf(theme.blockingFirst().theme)
            it.syncingProgress.indeterminateTintList = ColorStateList.valueOf(theme.blockingFirst().theme)
        }

        toggle.syncState()
        binding.toolbar.setNavigationOnClickListener {
            dismissKeyboard()
            homeIntent.onNext(Unit)
        }

        binding.toolbarSearchIcon.setOnClickListener {
            searchExpanded = true
            // Null the transition so all visibility changes happen instantly (badge hides,
            // search bar appears, icons hide) — avoids badge/search overlap and any
            // LayoutTransition interference. Then manually fade the search bar in.
            val lt = binding.toolbar.layoutTransition
            binding.toolbar.layoutTransition = null
            binding.toolbarSearch.alpha = 0f
            updateSearchVisibility()
            binding.toolbar.layoutTransition = lt
            binding.toolbarSearch.animate().alpha(1f).setDuration(300).start()
            binding.toolbarSearch.requestFocus()
        }

        binding.toolbarUnreadBadge.setOnClickListener {
            scrollToNextUnread()
        }

        binding.toolbarSearch.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.toolbarSearch.text.isNullOrEmpty()) {
                searchExpanded = false
                updateSearchVisibility()
            }
        }

        itemTouchCallback.adapter = conversationsAdapter
        conversationsAdapter.autoScrollToStart(binding.recyclerView)

        // Recount unread whenever the adapter updates (Realm writes are async, so render()
        // may fire before the write completes; this observer catches the post-write notification).
        conversationsAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = updateSearchVisibility()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = updateSearchVisibility()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = updateSearchVisibility()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = updateSearchVisibility()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = updateSearchVisibility()
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = updateSearchVisibility()
        })

        // Scroll-to-top button: appear on first scroll down, vanish when back at top
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val shouldShow = recyclerView.canScrollVertically(-1) && lastState?.page !is Searching
                if (shouldShow == scrollToTopVisible) return
                scrollToTopVisible = shouldShow
                binding.scrollToTop.animate().cancel()
                if (shouldShow) {
                    binding.scrollToTop.scaleX = 0.7f
                    binding.scrollToTop.scaleY = 0.7f
                    binding.scrollToTop.alpha = 0f
                    binding.scrollToTop.isVisible = true
                    binding.scrollToTop.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
                } else {
                    binding.scrollToTop.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(200)
                        .withEndAction { binding.scrollToTop.isVisible = false }.start()
                }
            }
        })
        binding.scrollToTop.setOnClickListener {
            val lm = binding.recyclerView.layoutManager as? LinearLayoutManager
            if ((lm?.findFirstVisibleItemPosition() ?: 0) > 20) {
                // Jump to position 20 so the smooth scroll covers enough distance to be visible.
                binding.recyclerView.scrollToPosition(20)
                binding.recyclerView.post { binding.recyclerView.smoothScrollToPosition(0) }
            } else {
                binding.recyclerView.smoothScrollToPosition(0)
            }
            binding.appBarLayout.setExpanded(true, true)
        }

        // Don't allow clicks to pass through the drawer layout
        binding.drawer.root.clicks().autoDisposable(scope()).subscribe()

        // Apply the theme color to all dynamic UI elements
        theme
                .autoDisposable(scope())
                .subscribe { theme ->
                    val density = resources.displayMetrics.density
                    val gradientEnd = colors.deriveGradientEndColor(theme.theme)
                    // The brand gradient is only used for the default violet; custom colors are flat.
                    val useGradient = colors.usesBrandGradient(theme.theme)
                    // Anything sitting on the theme color follows the status-bar icon rule. Computed
                    // here rather than read from toolbarContentColor: this subscription is registered
                    // in onCreate, so it fires before QkThemedActivity's own in onPostCreate.
                    val onTheme = colors.contentColorOnTheme(theme.theme)

                    // Drawer icons tint
                    val iconStates = arrayOf(
                            intArrayOf(android.R.attr.state_activated),
                            intArrayOf(-android.R.attr.state_activated))
                    ColorStateList(iconStates, intArrayOf(theme.theme,
                        resolveThemeColor(android.R.attr.textColorSecondary)
                    )).let { tintList ->
                        binding.drawer.inboxIcon.imageTintList = tintList
                        binding.drawer.archivedIcon.imageTintList = tintList
                    }

                    // Progress bar and misc drawer views
                    listOf(binding.drawer.plusBadge1, binding.drawer.plusBadge2).forEach { badge ->
                        badge.setBackgroundTint(theme.theme)
                        badge.setTextColor(theme.textPrimary)
                    }
                    syncingBinding.syncingProgress.progressTintList = ColorStateList.valueOf(theme.theme)
                    syncingBinding.syncingProgress.indeterminateTintList = ColorStateList.valueOf(theme.theme)
                    binding.drawer.plusIcon.setTint(theme.theme)
                    binding.drawer.rateIcon.setTint(theme.theme)

                    // Compose FAB — brand gradient (rounded rect) for the default color, solid otherwise
                    val cornerPx = 16f * density
                    val composeBackground = (if (useGradient) {
                        GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(theme.theme, gradientEnd))
                    } else {
                        GradientDrawable().apply { setColor(theme.theme) }
                    }).apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = cornerPx
                    }
                    val composeMask = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = cornerPx
                        setColor(Color.WHITE)
                    }
                    binding.compose.background = RippleDrawable(
                        ColorStateList.valueOf(ColorUtils.setAlphaComponent(onTheme, 0x40)),
                        composeBackground, composeMask
                    )
                    binding.compose.setTint(onTheme)

                    // Scroll-to-top — brand gradient (oval) for the default color, solid otherwise
                    val scrollBackground = (if (useGradient) {
                        GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(theme.theme, gradientEnd))
                    } else {
                        GradientDrawable().apply { setColor(theme.theme) }
                    }).apply { shape = GradientDrawable.OVAL }
                    val scrollMask = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.WHITE)
                    }
                    binding.scrollToTop.background = RippleDrawable(
                        ColorStateList.valueOf(ColorUtils.setAlphaComponent(onTheme, 0x40)),
                        scrollBackground, scrollMask
                    )
                    binding.scrollToTop.setTint(onTheme)

                    // Toolbar content: search icon, unread envelope and its count badge all sit on the
                    // themed toolbar, so they take the same color as the status-bar icons above them.
                    binding.toolbarSearchIcon.setTint(onTheme)
                    binding.toolbarUnreadIcon.setTint(onTheme)
                    binding.toolbarUnreadCount.setTextColor(onTheme)
                    // The badge is filled with the theme color on a toolbar of that same color, so its
                    // border is the only thing that separates the two: it has to stay legible too.
                    (binding.toolbarUnreadCount.background as? GradientDrawable)?.apply {
                        mutate()
                        setColor(theme.theme)
                        setStroke((1.5f * density).toInt(), onTheme)
                    }

                    // The drawer arrow / hamburger is drawn by ActionBarDrawerToggle, outside the
                    // toolbar's own icon tinting, so it needs the color applied here as well.
                    toggle.drawerArrowDrawable.color = onTheme

                    // Gradient only for the default violet brand color; all custom colors use solid fill
                    binding.drawer.drawerHeader.background = if (useGradient) {
                        GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(theme.theme, gradientEnd)
                        )
                    } else {
                        ColorDrawable(theme.theme)
                    }

                    // Activated drawer row highlight
                    val activeColor = Color.argb((255 * 0.15f).toInt(),
                        Color.red(theme.theme), Color.green(theme.theme), Color.blue(theme.theme))
                    val rippleColor = ColorStateList.valueOf(
                        resolveThemeColor(android.R.attr.colorControlHighlight))
                    listOf(binding.drawer.inbox, binding.drawer.archived).forEach { row ->
                        val states = StateListDrawable().apply {
                            addState(intArrayOf(android.R.attr.state_activated), ColorDrawable(activeColor))
                            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
                        }
                        row.background = RippleDrawable(rippleColor, states, null)
                    }
                }
    }

    // Edge-to-edge: the toolbar's themed background extends behind the status bar; the list draws
    // behind the transparent nav bar (clipToPadding is already false) while everything interactive
    // is lifted above it.
    override fun onApplyEdgeToEdgeInsets(top: Int, bottom: Int) {
        binding.toolbar.applyInsetTop(top)
        binding.recyclerView.applyInsetPadding(bottom = bottom)
        binding.compose.applyInsetBottomMargin(bottom)
        binding.scrollToTop.applyInsetBottomMargin(bottom)
        binding.drawer.root.applyInsetPadding(bottom = bottom)
        if (::snackbarBinding.isInitialized) snackbarBinding.root.applyInsetPadding(bottom = bottom)
        if (::syncingBinding.isInitialized) syncingBinding.root.applyInsetPadding(bottom = bottom)
        setupStatusBarScrim(top)
    }

    // Sizes the status-bar scrim to the inset and, once, hooks its alpha to the toolbar collapse so
    // it stays transparent at rest and fades in only as the list scrolls behind the status bar.
    private fun setupStatusBarScrim(top: Int) {
        binding.statusBarScrim.updateLayoutParams { height = top }
        binding.statusBarScrim.isVisible = true
        if (statusBarScrimListener == null) {
            statusBarScrimListener = AppBarLayout.OnOffsetChangedListener { appBar, offset ->
                val range = appBar.totalScrollRange
                val fraction = if (range > 0) -offset.toFloat() / range else 0f
                binding.statusBarScrim.alpha = fraction * STATUS_BAR_SCRIM_ALPHA
            }
            binding.appBarLayout.addOnOffsetChangedListener(statusBarScrimListener)
        }
    }

    override fun onNewIntent(intent: Intent?) =
        intent?.let {
            super.onNewIntent(intent)
            it.run(onNewIntentIntent::onNext)
        } ?: Unit

    override fun render(state: MainState) {
        if (state.hasError) {
            finish()
            return
        }

        dialogHost.render(state.dialog)

        conversationsAdapter.hasScheduledConversation = state.scheduledConversationIds

        val addContact = when (state.page) {
            is Inbox -> state.page.addContact
            is Archived -> state.page.addContact
            else -> false
        }

        val markPinned = when (state.page) {
            is Inbox -> state.page.markPinned
            is Archived -> state.page.markPinned
            else -> true
        }

        val markRead = when (state.page) {
            is Inbox -> state.page.markRead
            is Archived -> state.page.markRead
            else -> true
        }

        val selectedConversations = when (state.page) {
            is Inbox -> state.page.selected
            is Archived -> state.page.selected
            else -> 0
        }

        lastState = state
        updateSearchVisibility(state)

        binding.toolbar.menu.apply {
            findItem(R.id.select_all)?.isVisible =
                (conversationsAdapter.itemCount > 1) && selectedConversations != 0
            findItem(R.id.archive)?.isVisible =
                state.page is Inbox && selectedConversations != 0
            findItem(R.id.unarchive)?.isVisible =
                state.page is Archived && selectedConversations != 0
            findItem(R.id.delete)?.isVisible = selectedConversations != 0
            findItem(R.id.add)?.isVisible = addContact && selectedConversations != 0
            findItem(R.id.pin)?.isVisible = markPinned && selectedConversations != 0
            findItem(R.id.unpin)?.isVisible = !markPinned && selectedConversations != 0
            findItem(R.id.read)?.isVisible = ( markRead && selectedConversations != 0 ) ||
                    selectedConversations > 1
            findItem(R.id.unread)?.isVisible = ( !markRead && selectedConversations != 0 ) ||
                    selectedConversations > 1
            findItem(R.id.block)?.isVisible = selectedConversations != 0
            findItem(R.id.rename)?.isVisible = selectedConversations == 1
        }

        listOf(binding.drawer.plusBadge1, binding.drawer.plusBadge2).forEach { badge ->
            badge.isVisible = drawerBadgesExperiment.variant && !state.upgraded
        }
//        plus.isVisible = state.upgraded
        binding.drawer.plusBanner.isVisible = !state.upgraded
        // rate dialog permanently hidden

        binding.compose.setVisible(state.page is Inbox || state.page is Archived)
        conversationsAdapter.emptyView = binding.empty.takeIf {
            state.page is Inbox || state.page is Archived
        }
        searchAdapter.emptyView = binding.empty.takeIf { state.page is Searching }

        when (state.page) {
            is Inbox -> {
                showBackButton(state.page.selected > 0)
                title = if (state.page.selected == 0) getString(R.string.launcher_name)
                        else getString(R.string.main_title_selected, state.page.selected)
                if (binding.recyclerView.adapter !== conversationsAdapter)
                    binding.recyclerView.adapter = conversationsAdapter
                conversationsAdapter.updateData(state.page.data)
                itemTouchHelper.attachToRecyclerView(binding.recyclerView)
                binding.empty.setText(R.string.inbox_empty_text)
            }

            is Searching -> {
                showBackButton(true)
                if (binding.recyclerView.adapter !== searchAdapter) binding.recyclerView.adapter = searchAdapter
                searchAdapter.data = state.page.data ?: listOf()
                itemTouchHelper.attachToRecyclerView(null)
                binding.empty.setText(R.string.inbox_search_empty_text)
            }

            is Archived -> {
                showBackButton(state.page.selected > 0)
                title = when (state.page.selected != 0) {
                    true -> getString(R.string.main_title_selected, state.page.selected)
                    false -> getString(R.string.title_archived)
                }
                if (binding.recyclerView.adapter !== conversationsAdapter)
                    binding.recyclerView.adapter = conversationsAdapter
                conversationsAdapter.updateData(state.page.data)
                itemTouchHelper.attachToRecyclerView(null)
                binding.empty.setText(R.string.archived_empty_text)
            }

        }

        // Pin the toolbar during multi-selection, and while a bottom banner (the default-SMS /
        // permission hint or the syncing bar) is shown. Those banners are anchored to the bottom of
        // the scrolling content, which extends below the fold by the toolbar height, so a collapsing
        // toolbar leaves them off screen until the user scrolls. Pinning keeps the content within the
        // visible area so the banner stays put. Restore the collapsing toolbar once neither applies.
        val bannerShown = state.syncing !is SyncRepository.SyncProgress.Idle ||
            !state.defaultSms || !state.smsPermission || !state.contactPermission || !state.notificationPermission
        val toolbarParams = binding.toolbar.layoutParams as? AppBarLayout.LayoutParams
        if (selectedConversations > 0 || bannerShown) {
            toolbarParams?.scrollFlags = 0
            binding.appBarLayout.setExpanded(true, false)
        } else {
            toolbarParams?.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        }
        binding.toolbar.layoutParams = toolbarParams

        binding.drawer.inbox.isActivated = state.page is Inbox
        binding.drawer.archived.isActivated = state.page is Archived

        // The drawer is worked by the user as much as by the app, so what is applied here is a
        // change of mind, not the state of mind itself.
        //
        // Restating it on every draw meant asking the drawer to match a state that is only told
        // where the drawer went once the drawer has finished going there. A draw landing in that
        // gap, which a sync makes constant, found the drawer already gone and the state still
        // saying open, and pulled it back out. The two then disagreed the other way round, and
        // since the reopening was read as neither open nor closed while it lasted, nothing put it
        // back: the drawer stayed out and refused to close again.
        if (state.drawerOpen != appliedDrawerOpen) {
            appliedDrawerOpen = state.drawerOpen
            when (state.drawerOpen) {
                true -> binding.drawerLayout.openDrawer(GravityCompat.START)
                false -> binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        when (state.syncing) {
            is SyncRepository.SyncProgress.Idle -> {
                syncingBinding.root.isVisible = false
                snackbarBinding.root.isVisible = (!state.defaultSms ||
                        !state.smsPermission ||
                        !state.contactPermission ||
                        !state.notificationPermission)
            }

            is SyncRepository.SyncProgress.Running -> {
                syncingBinding.root.isVisible = true
                syncingBinding.syncingProgress.max = state.syncing.max
                progressAnimator.apply {
                    setIntValues(syncingBinding.syncingProgress.progress, state.syncing.progress)
                }.start()
                syncingBinding.syncingProgress.isIndeterminate = state.syncing.indeterminate
                snackbarBinding.root.isVisible = false
            }

            is SyncRepository.SyncProgress.ParsingEmojis -> {
                syncingBinding.root.isVisible = true
                syncingBinding.syncingLabel.setText(getString(R.string.main_sync_emojis))
                syncingBinding.syncingProgress.max = state.syncing.max
                progressAnimator.apply {
                    setIntValues(syncingBinding.syncingProgress.progress, state.syncing.progress)
                }.start()
                syncingBinding.syncingProgress.isIndeterminate = state.syncing.indeterminate
                snackbarBinding.root.isVisible = false
            }
        }

        when {
            !state.defaultSms -> {
                snackbarBinding.snackbarTitle.setText(R.string.main_default_sms_title)
                snackbarBinding.snackbarMessage.setText(R.string.main_default_sms_message)
                snackbarBinding.snackbarButton.setText(R.string.main_default_sms_change)
            }

            !state.smsPermission -> {
                snackbarBinding.snackbarTitle.setText(R.string.main_permission_required)
                snackbarBinding.snackbarMessage.setText(R.string.main_permission_sms)
                snackbarBinding.snackbarButton.setText(R.string.main_permission_allow)
            }

            !state.contactPermission -> {
                snackbarBinding.snackbarTitle.setText(R.string.main_permission_required)
                snackbarBinding.snackbarMessage.setText(R.string.main_permission_contacts)
                snackbarBinding.snackbarButton.setText(R.string.main_permission_allow)
            }

            !state.notificationPermission -> {
                snackbarBinding.snackbarTitle.setText(R.string.main_permission_required)
                snackbarBinding.snackbarMessage.setText(R.string.main_permission_notifications)
                snackbarBinding.snackbarButton.setText(R.string.main_permission_allow)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumedIntent.onNext(true)
        updateSearchVisibility()
    }

    override fun onPause() =
        super.onPause().also { activityResumedIntent.onNext(false) }

    override fun onDestroy() = super.onDestroy().also {
        disposables.dispose()
        // Closed without reporting it: the state keeps the dialog, and that is what lets the
        // rebuilt screen show it again instead of leaving its window behind.
        dialogHost.close()
    }

    override fun showBackButton(show: Boolean) =
        toggle.let {
            it.onDrawerSlide(binding.drawer.root, if (show) 1f else 0f)
            it.drawerArrowDrawable.color = toolbarContentColor
        }

    override fun requestDefaultSms() =
        navigator.showDefaultSmsDialog(this)

    override fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            permissions += Manifest.permission.POST_NOTIFICATIONS

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
    }

    override fun shouldShowNotificationRationale(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)

    override fun clearSearch() {
        dismissKeyboard()
        binding.toolbarSearch.text = null
        searchExpanded = false
        updateSearchVisibility()
    }

    override fun clearSelection() = conversationsAdapter.clearSelection()

    override fun toggleSelectAll() = conversationsAdapter.toggleSelectAll()

    override fun themeChanged() = binding.recyclerView.scrapViews()

    override fun showBlockingDialog(conversations: List<Long>, block: Boolean) {
        blockingDialog.show(conversations, block)
    }

    private fun buildDialog(spec: MainDialog): AlertDialog = when (spec) {
        is MainDialog.DeleteConversations -> {
            val count = spec.conversationIds.size
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(resources.getQuantityString(R.plurals.dialog_delete_message, count, count))
                .setPositiveButton(R.string.button_delete) { _, _ ->
                    confirmDeleteIntent.onNext(spec.conversationIds)
                }
                .setNegativeButton(R.string.button_cancel, null)
                .create()
        }

        is MainDialog.RenameConversation -> TextInputDialog(
            this,
            colors.theme().theme,
            getString(R.string.info_name)
        ) { name -> renameConversationIntent.onNext(spec.conversationId to name) }
            .setText(spec.currentName)
    }

    private fun updateSearchVisibility(state: MainState? = lastState) {
        if (state == null) return
        val isInbox = state.page is Inbox && (state.page as Inbox).selected == 0
        val isSearching = state.page is Searching
        val showInboxIcons = isInbox && !searchExpanded && !isSearching
        binding.toolbarSearch.setVisible(isSearching || (isInbox && searchExpanded))
        binding.toolbarSearchIcon.setVisible(showInboxIcons)
        binding.toolbarLogoGroup.setVisible(!isSearching && !(isInbox && searchExpanded))
        // Counted by Realm rather than by walking the list. This runs on every change to the list
        // and on every redraw of the screen, and counting in Kotlin built a Conversation object per
        // row just to read one flag. "unread" is lastMessage.read == false, the same query the
        // conversation repository already uses for it.
        val unreadCount = if (showInboxIcons) {
            (state.page as? Inbox)?.data?.where()?.equalTo("lastMessage.read", false)?.count()?.toInt() ?: 0
        } else 0
        if (showInboxIcons) {
            binding.toolbarUnreadCount.text = "$unreadCount"
        }
        binding.toolbarUnreadBadge.setVisible(showInboxIcons && unreadCount > 0)
        // Hide scroll-to-top when search is active
        if (isSearching || (isInbox && searchExpanded)) {
            scrollToTopVisible = false
            binding.scrollToTop.isVisible = false
        }
    }

    private fun scrollToNextUnread() {
        val unreadPositions = (0 until conversationsAdapter.itemCount)
            .filter { conversationsAdapter.getItem(it)?.unread == true }
        if (unreadPositions.isEmpty()) return

        val targetPos = unreadPositions[nextUnreadIndex % unreadPositions.size]
        nextUnreadIndex++

        val lm = binding.recyclerView.layoutManager as? LinearLayoutManager ?: return

        fun flashTarget() {
            binding.recyclerView.findViewHolderForAdapterPosition(targetPos)
                ?.itemView?.let { v ->
                    ObjectAnimator.ofFloat(v, "alpha", 1f, 0.25f, 1f, 0.25f, 1f)
                        .apply { duration = 1800 }
                        .start()
                }
        }

        val scroller = object : LinearSmoothScroller(this) {
            override fun calculateDtToFit(
                viewStart: Int, viewEnd: Int,
                boxStart: Int, boxEnd: Int,
                snapPreference: Int
            ): Int = (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
        }

        var idleListener: RecyclerView.OnScrollListener? = null
        idleListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    recyclerView.removeOnScrollListener(idleListener!!)
                    flashTarget()
                }
            }
        }
        binding.recyclerView.addOnScrollListener(idleListener)
        scroller.targetPosition = targetPos
        lm.startSmoothScroll(scroller)

        // Fallback: if item is already at the target (no scroll needed), animate immediately
        binding.recyclerView.post {
            if (binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                binding.recyclerView.removeOnScrollListener(idleListener!!)
                flashTarget()
            }
        }
    }


    override fun showChangelog(changelog: ChangelogManager.CumulativeChangelog) =
        changelogDialog.show(changelog)

    override fun showArchivedSnackbar(countConversationsArchived: Int, isArchiving: Boolean) =
        Snackbar.make(
            binding.drawerLayout,
            if (isArchiving) {
                resources.getQuantityString(R.plurals.toast_archived, countConversationsArchived, countConversationsArchived)
            } else {
                resources.getQuantityString(R.plurals.toast_unarchived, countConversationsArchived, countConversationsArchived)
            },
            if (countConversationsArchived < 10) Snackbar.LENGTH_LONG
            else Snackbar.LENGTH_INDEFINITE
        ).let {
            it.setAction(R.string.button_undo) { undoArchiveIntent.onNext(Unit) }
            it.setActionTextColor(colors.theme().theme)
            it.show()
        }

    override fun onCreateOptionsMenu(menu: Menu?) =
        menu?.let {
            menuInflater.inflate(R.menu.main, it)
            super.onCreateOptionsMenu(it)
        } ?: false

    override fun onOptionsItemSelected(item: MenuItem) =
        optionsItemIntent.onNext(item.itemId).let { true }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (searchExpanded && lastState?.page is Inbox && ev?.action == MotionEvent.ACTION_DOWN) {
            val toolbarBounds = Rect()
            binding.toolbar.getGlobalVisibleRect(toolbarBounds)
            if (!toolbarBounds.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                clearSearch()
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onBackPressed() {
        if (searchExpanded) {
            searchExpanded = false
            binding.toolbarSearch.text = null
            dismissKeyboard()
            updateSearchVisibility()
        } else {
            backPressedSubject.onNext(NavItem.BACK)
        }
    }

    override fun drawerToggled(opened: Boolean) {
        if (opened) {
            dismissKeyboard()
            if (searchExpanded) {
                searchExpanded = false
                binding.toolbarSearch.text = null
                updateSearchVisibility()
            }
            if (!binding.drawer.inbox.isInTouchMode)
                binding.drawer.inbox.requestFocus()
        }
    }
}
