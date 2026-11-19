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
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
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
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.GravityCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.drawerlayout.widget.DrawerLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import autodispose2.androidx.lifecycle.scope
import autodispose2.autoDispose
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding4.drawerlayout.drawerOpen
import com.jakewharton.rxbinding4.view.clicks
import com.jakewharton.rxbinding4.widget.textChanges
import dagger.android.AndroidInjection
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.ViewModelFactory
import io.openmessages.common.base.ThemedActivity
import io.openmessages.common.util.DialogHost
import io.openmessages.common.util.extensions.applyInsetBottomMargin
import io.openmessages.common.util.extensions.applyInsetPadding
import io.openmessages.common.util.extensions.applyInsetTop
import io.openmessages.common.util.extensions.autoScrollToStart
import io.openmessages.common.util.extensions.dismissKeyboard
import io.openmessages.common.util.extensions.dpToPx
import io.openmessages.common.util.extensions.resolveThemeColor
import io.openmessages.common.util.extensions.scrapViews
import io.openmessages.common.util.extensions.setTint
import io.openmessages.common.util.extensions.setVisible
import io.openmessages.common.util.extensions.showKeyboard
import io.openmessages.common.util.extensions.themeButtons
import io.openmessages.common.widget.GroupHeadingDecoration
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
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import javax.inject.Inject

/** How long the search row takes to unroll over the toolbar, or to roll back */
private const val SEARCH_REVEAL_MILLIS = 300L

class MainActivity : ThemedActivity(), MainView {

    @Inject lateinit var blockingDialog: BlockingDialog
    @Inject lateinit var disposables: CompositeDisposable
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var conversationsAdapter: ConversationsAdapter
    @Inject lateinit var searchAdapter: SearchAdapter
    @Inject lateinit var itemTouchCallback: ConversationItemTouchCallback
    @Inject lateinit var viewModelFactory: ViewModelFactory

    private lateinit var binding: MainActivityBinding
    private lateinit var snackbarBinding: MainPermissionHintBinding
    private lateinit var syncingBinding: MainSyncingBinding

    private var searchExpanded = false

    /** Whether the search row is out, or on its way out */
    private var searchBarShown = false
    private var searchReveal: ValueAnimator? = null
    private var lastState: MainState? = null
    private var nextUnreadIndex = 0
    private var scrollToTopVisible = false

    /** What decides what shows behind the status bar, see [updateStatusBarBackdrop] */
    private var statusBarHeight = 0
    private var appBarOffset = 0
    private var drawerOffset = 0f

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
//                help.clicks().map { NavItem.HELP },
                binding.drawer.invite.clicks().map { NavItem.INVITE }))
    }
    override val optionsItemIntent: Subject<Int> = PublishSubject.create()
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
        ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]
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
        viewModel.bindView(this)
        onNewIntentIntent.onNext(intent)
        onBackPressedDispatcher.addCallback(this) {
            if (searchExpanded) {
                searchExpanded = false
                binding.toolbarSearch.text = null
                dismissKeyboard()
                updateSearchVisibility()
            } else {
                backPressedSubject.onNext(NavItem.BACK)
            }
        }

        binding.appBarLayout.addOnOffsetChangedListener { _, offset ->
            appBarOffset = offset
            updateStatusBarBackdrop()
        }
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                drawerOffset = slideOffset
                updateStatusBarBackdrop()
            }

            override fun onDrawerOpened(drawerView: View) = updateDrawerSwipe()
            override fun onDrawerClosed(drawerView: View) = updateDrawerSwipe()
        })
        prefs.drawerSwipe.asObservable()
            .autoDispose(scope(Lifecycle.Event.ON_DESTROY))
            .subscribe { updateDrawerSwipe() }

        snackbarBinding = MainPermissionHintBinding.bind(binding.snackbar.inflate()).also {
            it.snackbarButton.clicks()
                .autoDispose(scope(Lifecycle.Event.ON_DESTROY))
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
            updateSearchVisibility()
            binding.toolbarSearch.showKeyboard()
        }
        binding.searchClear.setOnClickListener { binding.toolbarSearch.text = null }
        binding.toolbarSearch.textChanges()
            .autoDispose(scope())
            .subscribe { text -> binding.searchClear.isInvisible = text.isEmpty() }

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
        binding.recyclerView.addItemDecoration(GroupHeadingDecoration(binding.recyclerView))
        conversationsAdapter.autoScrollToStart(binding.recyclerView)

        // Recount unread whenever the list on screen changes
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
        binding.drawer.root.clicks().autoDispose(scope()).subscribe()

        // Apply the theme color to all dynamic UI elements
        theme
                .autoDispose(scope())
                .subscribe { theme ->
                    val density = resources.displayMetrics.density
                    // Anything sitting on the theme color follows the status-bar icon rule. Computed
                    // here rather than read from toolbarContentColor: this subscription is registered
                    // in onCreate, so it fires before ThemedActivity's own in onPostCreate.
                    val onTheme = colors.contentColorOnTheme(theme.theme)

                    // Drawer: the entry showing is a pill in the theme colour, its icon and text in the
                    // colour that reads on it; the others keep their own icon colour
                    val activated = intArrayOf(android.R.attr.state_activated)
                    fun onPill(rest: Int) = ColorStateList(arrayOf(activated, intArrayOf()), intArrayOf(onTheme, rest))
                    binding.drawer.inboxIcon.imageTintList = onPill(theme.theme)
                    binding.drawer.archivedIcon.imageTintList =
                        onPill(ContextCompat.getColor(this, R.color.drawerIconArchived))
                    val textPrimary = resolveThemeColor(android.R.attr.textColorPrimary)
                    binding.drawer.inboxText.setTextColor(onPill(textPrimary))
                    binding.drawer.archivedText.setTextColor(onPill(textPrimary))
                    binding.drawer.inboxSummary.setTextColor(ColorStateList(arrayOf(activated, intArrayOf()),
                        intArrayOf(ColorUtils.setAlphaComponent(onTheme, 0xCC),
                            resolveThemeColor(android.R.attr.textColorSecondary))))

                    // Progress bar and misc drawer views
                    syncingBinding.syncingProgress.progressTintList = ColorStateList.valueOf(theme.theme)
                    syncingBinding.syncingProgress.indeterminateTintList = ColorStateList.valueOf(theme.theme)
                    binding.drawer.rateIcon.setTint(theme.theme)

                    // Compose FAB, a rounded square in the theme colour
                    val cornerPx = 16f * density
                    val composeBackground = GradientDrawable().apply {
                        setColor(theme.theme)
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

                    // Scroll-to-top, a disc in the theme colour
                    val scrollBackground = GradientDrawable().apply {
                        setColor(theme.theme)
                        shape = GradientDrawable.OVAL
                    }
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

                    // The search row lies on the toolbar's background, so it reads the same way
                    binding.searchClear.setTint(onTheme)
                    binding.toolbarSearch.setTextColor(onTheme)
                    binding.toolbarSearch.setHintTextColor(ColorUtils.setAlphaComponent(onTheme, 0xB3))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        binding.toolbarSearch.textCursorDrawable?.setTint(onTheme)
                    }

                    // The drawer arrow / hamburger is drawn by ActionBarDrawerToggle, outside the
                    // toolbar's own icon tinting, so it needs the color applied here as well.
                    toggle.drawerArrowDrawable.color = onTheme

                    // Drawer logo tile, in the theme colour like the compose button
                    binding.drawer.drawerLogo.background = GradientDrawable().apply {
                        setColor(theme.theme)
                        cornerRadius = 14f * density
                    }
                    binding.drawer.drawerLogo.clipToOutline = true

                    // Every drawer entry ripples within its pill; the entry showing fills it
                    val pillRadius = 26f * density
                    fun pill(color: Int) = GradientDrawable().apply { cornerRadius = pillRadius; setColor(color) }
                    val rippleColor = ColorStateList.valueOf(
                        resolveThemeColor(android.R.attr.colorControlHighlight))
                    with(binding.drawer) {
                        listOf(inbox, archived, backup, scheduled, blocking, messageUtils, templates,
                            settings, invite, about)
                    }.forEach { row ->
                        val states = StateListDrawable().apply {
                            addState(activated, pill(theme.theme))
                            addState(intArrayOf(), pill(Color.TRANSPARENT))
                        }
                        row.background = RippleDrawable(rippleColor, states, pill(Color.WHITE))
                    }
                }
    }

    // Edge-to-edge: the toolbar's themed background, and the drawer header's, extend behind the
    // status bar; the list draws behind the transparent nav bar (clipToPadding is already false)
    // while everything interactive is lifted above it. Without it, the screen keeps clear of both
    // bars like any other.
    override fun onApplyEdgeToEdgeInsets(top: Int, bottom: Int) {
        if (!isEdgeToEdge()) {
            super.onApplyEdgeToEdgeInsets(top, bottom)
            return
        }
        statusBarHeight = top
        findViewById<View>(android.R.id.content)?.applyInsetPadding(left = sideInsetLeft, right = sideInsetRight)
        binding.toolbar.applyInsetTop(top)
        binding.searchBar.applyInsetTop(top)
        binding.drawer.drawerHeader.applyInsetTop(top)
        binding.recyclerView.applyInsetPadding(bottom = bottom)
        binding.compose.applyInsetBottomMargin(bottom)
        binding.scrollToTop.applyInsetBottomMargin(bottom)
        binding.drawer.root.applyInsetPadding(bottom = bottom)
        if (::snackbarBinding.isInitialized) snackbarBinding.root.applyInsetPadding(bottom = bottom)
        if (::syncingBinding.isInitialized) syncingBinding.root.applyInsetPadding(bottom = bottom)
        updateStatusBarBackdrop()
    }

    /**
     * In edge-to-edge the status bar shows the toolbar, in the theme colour, until the toolbar
     * scrolls away from under it, leaving the list, or the drawer slides in over it, its own
     * background reaching up behind the bar
     */
    /**
     * Swiping in from the edge opens the menu only when the setting is on; once open, it can always
     * be swiped closed
     */
    private fun updateDrawerSwipe() {
        val swipe = prefs.drawerSwipe.get() || binding.drawerLayout.isDrawerOpen(GravityCompat.START)
        binding.drawerLayout.setDrawerLockMode(
            if (swipe) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    private fun updateStatusBarBackdrop() {
        val backdrop = when {
            !isEdgeToEdge() -> null
            drawerOffset >= 0.5f -> resolveThemeColor(R.attr.bubbleColor)
            // Set by the insets ahead of any layout, unlike the measured height
            binding.toolbar.layoutParams.height + appBarOffset < statusBarHeight / 2 ->
                resolveThemeColor(android.R.attr.windowBackground)
            else -> null
        }
        setStatusBarBackdrop(backdrop)
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
        conversationsAdapter.unreadCounts = state.unreadCounts

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

        // rate dialog permanently hidden

        binding.compose.setVisible(state.page is Inbox || state.page is Archived)
        conversationsAdapter.emptyView = binding.empty.takeIf {
            state.page is Inbox || state.page is Archived
        }
        searchAdapter.emptyView = binding.empty.takeIf { state.page is Searching }

        when (state.page) {
            is Inbox -> {
                showMenuButton(state.page.selected == 0)
                title = if (state.page.selected == 0) getString(R.string.launcher_name)
                        else getString(R.string.main_title_selected, state.page.selected)
                if (binding.recyclerView.adapter !== conversationsAdapter)
                    binding.recyclerView.adapter = conversationsAdapter
                state.page.data?.let { conversationsAdapter.data = it }
                itemTouchHelper.attachToRecyclerView(binding.recyclerView)
                binding.empty.setText(R.string.inbox_empty_text)
            }

            is Searching -> {
                showMenuButton(false)
                if (binding.recyclerView.adapter !== searchAdapter) binding.recyclerView.adapter = searchAdapter
                searchAdapter.data = state.page.data ?: listOf()
                itemTouchHelper.attachToRecyclerView(null)
                binding.empty.setText(R.string.inbox_search_empty_text)
            }

            is Archived -> {
                showMenuButton(state.page.selected == 0)
                title = when (state.page.selected != 0) {
                    true -> getString(R.string.main_title_selected, state.page.selected)
                    false -> getString(R.string.title_archived)
                }
                if (binding.recyclerView.adapter !== conversationsAdapter)
                    binding.recyclerView.adapter = conversationsAdapter
                state.page.data?.let { conversationsAdapter.data = it }
                itemTouchHelper.attachToRecyclerView(null)
                binding.empty.setText(R.string.archived_empty_text)
            }

        }

        // The conversations run edge to edge, as Google Messages lists them; search results keep
        // the gutter their blocks sit in
        val gutter = if (binding.recyclerView.adapter === conversationsAdapter) 0 else LIST_GUTTER_DP.dpToPx(this)
        binding.recyclerView.updatePaddingRelative(start = gutter, end = gutter)

        // Pin the toolbar during multi-selection, and while a bottom banner (the default-SMS /
        // permission hint or the syncing bar) is shown. Those banners are anchored to the bottom of
        // the scrolling content, which extends below the fold by the toolbar height, so a collapsing
        // toolbar leaves them off screen until the user scrolls. Pinning keeps the content within the
        // visible area so the banner stays put. Restore the collapsing toolbar once neither applies.
        val bannerShown = state.syncing !is SyncRepository.SyncProgress.Idle ||
            !state.defaultSms || !state.smsPermission || !state.contactPermission || !state.notificationPermission
        val toolbarParams = binding.toolbarFrame.layoutParams as? AppBarLayout.LayoutParams
        if (selectedConversations > 0 || bannerShown) {
            toolbarParams?.scrollFlags = 0
            binding.appBarLayout.setExpanded(true, false)
        } else {
            toolbarParams?.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        }
        binding.toolbarFrame.layoutParams = toolbarParams

        binding.drawer.inbox.isActivated = state.page is Inbox
        binding.drawer.archived.isActivated = state.page is Archived
        // Only the inbox knows how many are unread, so the count stays as it was on other pages
        if (state.page is Inbox) {
            val unread = state.page.data?.count { conversation -> conversation.unread } ?: 0
            binding.drawer.inboxSummary.setVisible(unread > 0)
            binding.drawer.inboxSummary.text =
                resources.getQuantityString(R.plurals.drawer_unread_count, unread, unread)
        }

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

    /**
     * The menu button that opens the drawer. Hidden while selecting or searching, rather than turned
     * into a back arrow: Android's own back undoes those.
     */
    private fun showMenuButton(show: Boolean) {
        binding.toolbar.navigationIcon = if (show) toggle.drawerArrowDrawable else null
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

    /**
     * Unrolls the search row from the right edge over the whole toolbar, or rolls it back there,
     * from wherever an unfinished roll left it
     */
    private fun showSearchBar(show: Boolean) {
        if (show == searchBarShown) return
        searchBarShown = show
        val bar = binding.searchBar
        if (show) {
            // A copy of the toolbar's own background
            bar.background = binding.toolbar.background?.constantState?.newDrawable()?.mutate()
            bar.isVisible = true
        }
        val from = bar.clipBounds?.left ?: if (show) bar.width else 0
        searchReveal?.cancel()
        searchReveal = ValueAnimator.ofInt(from, if (show) 0 else bar.width).apply {
            duration = SEARCH_REVEAL_MILLIS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animator ->
                bar.clipBounds = Rect(animator.animatedValue as Int, 0, bar.width, bar.height)
            }
            doOnEnd {
                bar.clipBounds = null
                if (!searchBarShown) bar.isInvisible = true
            }
            start()
        }
    }

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
            colors,
            colors.theme().theme,
            getString(R.string.info_name)
        ) { name -> renameConversationIntent.onNext(spec.conversationId to name) }
            .setText(spec.currentName)
    }

    private fun updateSearchVisibility(state: MainState? = lastState) {
        if (state == null) return
        val isInbox = state.page is Inbox && state.page.selected == 0
        val isSearching = state.page is Searching
        // The toolbar keeps its title and icons under the search row, which uncovers them as it
        // rolls back
        val showInboxIcons = isInbox
        showSearchBar(isSearching || (isInbox && searchExpanded))
        binding.toolbarSearchIcon.setVisible(showInboxIcons)
        binding.toolbarLogoGroup.setVisible(!isSearching)
        val unreadCount = if (showInboxIcons) {
            state.page.data?.count { conversation -> conversation.unread } ?: 0
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
            .filter { conversationsAdapter.getItem(it).unread }
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

        val idleListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    recyclerView.removeOnScrollListener(this)
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
                binding.recyclerView.removeOnScrollListener(idleListener)
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

    companion object {
        /** The side gutter the search results' blocks sit in */
        private const val LIST_GUTTER_DP = 12
    }

}
