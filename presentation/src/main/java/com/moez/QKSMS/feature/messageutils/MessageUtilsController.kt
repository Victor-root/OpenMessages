package io.openmessages.feature.messageutils

import android.animation.ObjectAnimator
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.jakewharton.rxbinding2.view.clicks
import io.openmessages.R
import io.openmessages.common.base.QkController
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.animateLayoutChanges
import io.openmessages.common.util.extensions.setTint
import io.openmessages.common.util.extensions.themeButtons
import io.openmessages.databinding.MessageUtilsControllerBinding
import io.openmessages.feature.settings.autodelete.AutoDeleteDialog
import io.openmessages.injection.appComponent
import io.openmessages.repository.MessageRepository
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume

class MessageUtilsController : QkController<MessageUtilsControllerBinding, MessageUtilsView, MessageUtilsState, MessageUtilsPresenter>(), MessageUtilsView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): MessageUtilsControllerBinding =
        MessageUtilsControllerBinding.inflate(inflater, container, false)

    @Inject lateinit var context: Context
    @Inject lateinit var colors: Colors
    @Inject override lateinit var presenter: MessageUtilsPresenter
    private val autoDeleteDialog: AutoDeleteDialog by lazy {
        AutoDeleteDialog(activity!!, colors.theme().theme, autoDeleteSubject::onNext)
    }
    private val autoDeleteSubject: Subject<Int> = PublishSubject.create()
    override val autoDeduplicateClickIntent by lazy { binding.autoDeduplicate.clicks() }
    override val deduplicateClickIntent by lazy { binding.deduplicateMessages.clicks() }
    override val autoDeleteClickIntent by lazy { binding.autoDelete.clicks() }

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    override fun onViewCreated() {
        super.onViewCreated()
        // The deduplication bar takes colorAccent otherwise, which is the static brand violet.
        binding.deduplicationProgress.setTint(colors.theme().theme)
        binding.root.postDelayed({
            binding.parent.animateLayoutChanges = true
        }, 100)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        setTitle(R.string.message_management_title)
        showBackButton(true)
        presenter.bindIntents(this)
    }

    override fun render(state: MessageUtilsState) {
        binding.autoDeduplicate.checkbox?.isChecked = state.autoDeduplicateMessages

        val deduplicationProgress = binding.deduplicationProgress
        val progressAnimator = ObjectAnimator.ofInt(deduplicationProgress, "progress", 0, 0)

        when (state.deduplicationProgress) {
            is MessageRepository.DeduplicationProgress.Idle -> {
                deduplicationProgress.isVisible = false
            }

            is MessageRepository.DeduplicationProgress.Running -> {
                deduplicationProgress.isVisible = true
                deduplicationProgress.max = state.deduplicationProgress.max
                progressAnimator
                    .apply {
                        setIntValues(
                            deduplicationProgress.progress,
                            state.deduplicationProgress.progress
                        )
                    }
                    .start()
                deduplicationProgress.isIndeterminate =
                    state.deduplicationProgress.indeterminate
            }
        }

        binding.autoDelete.summary = when (state.autoDelete) {
            0 -> context.getString(R.string.settings_auto_delete_never)
            else -> context.resources.getQuantityString(
                R.plurals.settings_auto_delete_summary, state.autoDelete, state.autoDelete)
        }
    }

    override fun showDeduplicationConfirmationDialog(): Single<Boolean> = Single.create { emitter ->
        AlertDialog.Builder(activity!!)
            .setTitle(R.string.deduplicate_messages_title)
            .setMessage(R.string.deduplicate_message_confirmation_dialog_message)
            .setPositiveButton(R.string.button_continue) { _, _ -> emitter.onSuccess(true) }
            .setNegativeButton(R.string.button_cancel) { _, _ -> emitter.onSuccess(false) }
            .setCancelable(false)
            .show()
            .themeButtons(colors.theme().theme)
    }

    override fun handleDeduplicationResult(resIdString: Int) {
        binding.deduplicationProgressText.isVisible = true
        binding.deduplicationProgressText.setText(resIdString)
    }

    override fun autoDeleteChanged(): Observable<Int> = autoDeleteSubject

    override fun showAutoDeleteDialog(days: Int) = autoDeleteDialog.setExpiry(days).show()

    override suspend fun showAutoDeleteWarningDialog(messages: Int): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            AlertDialog.Builder(activity!!)
                .setTitle(R.string.settings_auto_delete_warning)
                .setMessage(context.resources.getString(R.string.settings_auto_delete_warning_message, messages))
                .setOnCancelListener { cont.resume(false) }
                .setNegativeButton(R.string.button_cancel) { _, _ -> cont.resume(false) }
                .setPositiveButton(R.string.button_yes) { _, _ -> cont.resume(true) }
                .show()
                .themeButtons(colors.theme().theme)
        }
    }
}
