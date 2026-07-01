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
package io.openmessages.feature.backup

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.core.view.isVisible
import com.jakewharton.rxbinding2.view.clicks
import io.openmessages.R
import io.openmessages.common.base.QkController
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.getLabel
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.setNegativeButton
import io.openmessages.common.util.extensions.setPositiveButton
import io.openmessages.common.util.extensions.setShowing
import io.openmessages.common.util.extensions.setTint
import io.openmessages.common.util.extensions.themeButtons
import io.openmessages.common.widget.PreferenceView
import io.openmessages.injection.appComponent
import io.openmessages.model.BackupCategory
import io.openmessages.model.BackupFolder
import io.openmessages.repository.BackupRepository
import io.openmessages.util.Preferences
import io.openmessages.databinding.BackupControllerBinding
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class BackupController : QkController<BackupControllerBinding, BackupView, BackupState, BackupPresenter>(), BackupView {

    @Inject override lateinit var presenter: BackupPresenter
    @Inject lateinit var colors: Colors

    private val restoreErrorConfirmSubject: Subject<Unit> = PublishSubject.create()

    private val exactAlarmGrantSubject: Subject<Unit> = PublishSubject.create()
    private val exactAlarmSkipSubject: Subject<Unit> = PublishSubject.create()
    private val exactAlarmSettingsClosedSubject: Subject<Unit> = PublishSubject.create()

    private val stopRestoreConfirmSubject: Subject<Unit> = PublishSubject.create()
    private val stopRestoreCancelSubject: Subject<Unit> = PublishSubject.create()

    private val documentTreeSelectedSubject: Subject<Uri> = PublishSubject.create()
    private val restoreFolderSelectedSubject: Subject<Uri> = PublishSubject.create()

    private val autoBackupFrequencySelectedSubject: Subject<Int> = PublishSubject.create()
    private val backupCategoriesSelectedSubject: Subject<Set<BackupCategory>> = PublishSubject.create()
    private val restoreSourceSelectedSubject: Subject<Pair<Uri, BackupFolder>> = PublishSubject.create()
    private val restoreCategoriesSelectedSubject: Subject<Triple<Uri, String, Set<BackupCategory>>> = PublishSubject.create()

    private val stopRestoreDialog by lazy {
        AlertDialog.Builder(activity!!)
                .setTitle(R.string.backup_restore_stop_title)
                .setMessage(R.string.backup_restore_stop_message)
                .setPositiveButton(R.string.button_stop, stopRestoreConfirmSubject)
                .setNegativeButton(R.string.button_cancel, stopRestoreCancelSubject)
                .setCancelable(false)
                .create()
                .themeButtons(colors.theme().theme)
    }

    private val selectedBackupErrorDialog by lazy {
        AlertDialog.Builder(activity!!)
                .setTitle(R.string.backup_selected_backup_error_title)
                .setMessage(R.string.backup_selected_backup_error_message)
                .setPositiveButton(R.string.button_continue, restoreErrorConfirmSubject)
                .setCancelable(false)
                .create()
                .themeButtons(colors.theme().theme)
    }

    private val exactAlarmDialog by lazy {
        AlertDialog.Builder(activity!!)
                .setTitle(R.string.backup_exact_alarm_title)
                .setMessage(R.string.backup_exact_alarm_message)
                .setPositiveButton(R.string.backup_exact_alarm_grant, exactAlarmGrantSubject)
                .setNegativeButton(R.string.backup_exact_alarm_skip, exactAlarmSkipSubject)
                .setCancelable(false)
                .create()
                .themeButtons(colors.theme().theme)
    }

    private lateinit var openDirectory: ActivityResultLauncher<Uri?>
    private lateinit var openRestoreDirectory: ActivityResultLauncher<Uri?>
    private lateinit var exactAlarmSettings: ActivityResultLauncher<Intent>

    init {
        appComponent.inject(this)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): BackupControllerBinding =
        BackupControllerBinding.inflate(inflater, container, false)

    override fun onContextAvailable(context: Context) {
        openDirectory = themedActivity!!
            .registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                uri?.let(documentTreeSelectedSubject::onNext)
            }

        openRestoreDirectory = themedActivity!!
            .registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                uri?.let(restoreFolderSelectedSubject::onNext)
            }

        // Fires when the user returns from the exact-alarm settings, so restore can start only then
        exactAlarmSettings = themedActivity!!
            .registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                exactAlarmSettingsClosedSubject.onNext(Unit)
            }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.backup_title)
        showBackButton(true)
    }

    override fun onViewCreated() {
        super.onViewCreated()

        themedActivity?.colors?.theme()?.let { theme ->
            binding.progressBar.indeterminateTintList = ColorStateList.valueOf(theme.theme)
            binding.progressBar.progressTintList = ColorStateList.valueOf(theme.theme)
            binding.fab.setBackgroundTint(theme.theme)
            binding.fabIcon.setTint(theme.textPrimary)
            binding.fabLabel.setTextColor(theme.textPrimary)
        }

        // Make the list titles bold
        binding.linearLayout.children
            .mapNotNull { it as? PreferenceView }
            .map { it.titleView }
            .forEach { it.setTypeface(it.typeface, Typeface.BOLD) }
    }

    override fun render(state: BackupState) {
        when {
            state.backupProgress.running -> {
                binding.progressIcon.setImageResource(R.drawable.ic_file_upload_black_24dp)
                binding.progressTitle.setText(R.string.backup_backing_up)
                binding.progressSummary.text = state.backupProgress.getLabel(activity!!)
                binding.progressSummary.isVisible = binding.progressSummary.text.isNotEmpty()
                binding.progressCancel.isVisible = false
                val running = (state.backupProgress as? BackupRepository.Progress.Running)
                binding.progressBar.isVisible =
                    state.backupProgress.indeterminate || (running?.max ?: 0) > 0
                binding.progressBar.isIndeterminate = state.backupProgress.indeterminate
                binding.progressBar.max = running?.max ?: 0
                binding.progressBar.progress = running?.count ?: 0
                binding.progress.isVisible = true
                binding.fab.isVisible = false
            }

            state.restoreProgress.running -> {
                binding.progressIcon.setImageResource(R.drawable.ic_file_download_black_24dp)
                binding.progressTitle.setText(R.string.backup_restoring)
                binding.progressSummary.text = state.restoreProgress.getLabel(activity!!)
                binding.progressSummary.isVisible = binding.progressSummary.text.isNotEmpty()
                binding.progressCancel.isVisible = true
                val running = (state.restoreProgress as? BackupRepository.Progress.Running)
                binding.progressBar.isVisible =
                    state.restoreProgress.indeterminate || (running?.max ?: 0) > 0
                binding.progressBar.isIndeterminate = state.restoreProgress.indeterminate
                binding.progressBar.max = running?.max ?: 0
                binding.progressBar.progress = running?.count ?: 0
                binding.progress.isVisible = true
                binding.fab.isVisible = false
            }

            else -> {
                binding.progress.isVisible = false
                binding.fab.isVisible = true
            }
        }

        if (state.backupLocation.isNotEmpty()) binding.location.summary = state.backupLocation

        binding.autoBackup.summary = autoBackupFrequencyLabel(state.autoBackupFrequency)

        selectedBackupErrorDialog.setShowing(state.showSelectedBackupError)

        exactAlarmDialog.setShowing(state.showExactAlarmDialog)

        stopRestoreDialog.setShowing(state.showStopRestoreDialog)

        binding.fabIcon.setImageResource(when (state.upgraded) {
            true -> R.drawable.ic_file_upload_black_24dp
            false -> R.drawable.ic_star_black_24dp
        })

        binding.fabLabel.setText(when (state.upgraded) {
            true -> R.string.backup_now
            false -> R.string.title_qksms_plus
        })
    }

    override fun setBackupLocationClicks(): Observable<*> = binding.location.clicks()

    override fun autoBackupClicks(): Observable<*> = binding.autoBackup.clicks()

    override fun autoBackupFrequencySelected(): Observable<Int> = autoBackupFrequencySelectedSubject

    override fun restoreClicks(): Observable<*> = binding.restore.clicks()

    override fun selectedBackupErrorClicks(): Observable<*> = restoreErrorConfirmSubject

    override fun exactAlarmGrantClicks(): Observable<*> = exactAlarmGrantSubject

    override fun exactAlarmSkipClicks(): Observable<*> = exactAlarmSkipSubject

    override fun exactAlarmSettingsClosed(): Observable<*> = exactAlarmSettingsClosedSubject

    override fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exactAlarmSettings.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${activity!!.packageName}")))
        } else {
            // The dialog only appears on Android 12+, but stay safe: proceed straight to the restore
            exactAlarmSettingsClosedSubject.onNext(Unit)
        }
    }

    override fun stopRestoreClicks(): Observable<*> = binding.progressCancel.clicks()

    override fun stopRestoreConfirmed(): Observable<*> = stopRestoreConfirmSubject

    override fun stopRestoreCancel(): Observable<*> = stopRestoreCancelSubject

    override fun backupClicks(): Observable<*> = binding.fab.clicks()

    override fun documentTreeSelected(): Observable<Uri> = documentTreeSelectedSubject

    override fun restoreFolderSelected(): Observable<Uri> = restoreFolderSelectedSubject

    override fun backupCategoriesSelected(): Observable<Set<BackupCategory>> = backupCategoriesSelectedSubject

    override fun restoreSourceSelected(): Observable<Pair<Uri, BackupFolder>> = restoreSourceSelectedSubject

    override fun restoreCategoriesSelected(): Observable<Triple<Uri, String, Set<BackupCategory>>> =
        restoreCategoriesSelectedSubject

    override fun selectFolder(initialUri: Uri) {
        openDirectory.launch(initialUri)
    }

    override fun selectRestoreFolder(initialUri: Uri) {
        openRestoreDirectory.launch(initialUri)
    }

    override fun showAutoBackupFrequencyPicker(current: Int) {
        val labels = arrayOf(
                activity!!.getString(R.string.backup_auto_never),
                activity!!.getString(R.string.backup_auto_daily),
                activity!!.getString(R.string.backup_auto_weekly),
                activity!!.getString(R.string.backup_auto_custom))

        // Pre-tick the preset that matches the stored day count, or "Custom" for any other value
        val checked = when (current) {
            Preferences.BACKUP_FREQUENCY_NEVER -> 0
            Preferences.BACKUP_FREQUENCY_DAILY -> 1
            Preferences.BACKUP_FREQUENCY_WEEKLY -> 2
            else -> 3
        }

        AlertDialog.Builder(activity!!)
                .setTitle(R.string.backup_auto_title)
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    dialog.dismiss()
                    when (which) {
                        0 -> autoBackupFrequencySelectedSubject.onNext(Preferences.BACKUP_FREQUENCY_NEVER)
                        1 -> autoBackupFrequencySelectedSubject.onNext(Preferences.BACKUP_FREQUENCY_DAILY)
                        2 -> autoBackupFrequencySelectedSubject.onNext(Preferences.BACKUP_FREQUENCY_WEEKLY)
                        else -> showCustomFrequencyDialog(current)
                    }
                }
                .setNegativeButton(R.string.button_cancel, null)
                .create()
                .themeButtons(colors.theme().theme)
                .show()
    }

    /** Lets the user type any number of days between automatic backups. */
    private fun showCustomFrequencyDialog(current: Int) {
        val margin = (activity!!.resources.displayMetrics.density * 20).toInt()
        val input = EditText(activity!!).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setHint(R.string.backup_auto_custom_hint)
            current.takeIf { it > 1 }?.let { setText(it.toString()) }
            setSelection(text.length)
        }
        val container = FrameLayout(activity!!).apply {
            setPadding(margin, margin / 2, margin, 0)
            addView(input)
        }

        AlertDialog.Builder(activity!!)
                .setTitle(R.string.backup_auto_custom_title)
                .setView(container)
                .setPositiveButton(R.string.button_save) { _, _ ->
                    input.text.toString().toIntOrNull()?.coerceIn(1, 365)?.let { days ->
                        autoBackupFrequencySelectedSubject.onNext(days)
                    }
                }
                .setNegativeButton(R.string.button_cancel, null)
                .create()
                .themeButtons(colors.theme().theme)
                .show()
    }

    override fun showBackupCategoryPicker() {
        val categories = BackupCategory.values()
        val labels = categories.map { activity!!.getString(categoryLabel(it)) }.toTypedArray()
        val checked = BooleanArray(categories.size) { true }

        AlertDialog.Builder(activity!!)
                .setTitle(R.string.backup_categories_title)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
                .setPositiveButton(R.string.backup_now) { _, _ ->
                    val selected = categories.filterIndexed { index, _ -> checked[index] }.toSet()
                    if (selected.isNotEmpty()) backupCategoriesSelectedSubject.onNext(selected)
                }
                .setNegativeButton(R.string.button_cancel, null)
                .create()
                .themeButtons(colors.theme().theme)
                .show()
    }

    override fun showRestoreSourcePicker(folder: Uri, backups: List<BackupFolder>, labels: List<String>) {
        if (backups.isEmpty()) return

        AlertDialog.Builder(activity!!)
                .setTitle(R.string.backup_restore_source_title)
                .setItems(labels.toTypedArray()) { _, which ->
                    restoreSourceSelectedSubject.onNext(folder to backups[which])
                }
                .setNegativeButton(R.string.button_cancel, null)
                .create()
                .themeButtons(colors.theme().theme)
                .show()
    }

    override fun showRestoreCategoryPicker(folder: Uri, folderName: String, available: Set<BackupCategory>, dateLabel: String) {
        val categories = BackupCategory.values().filter { it in available }
        if (categories.isEmpty()) return

        val labels = categories.map { activity!!.getString(categoryLabel(it)) }.toTypedArray()
        val checked = BooleanArray(categories.size) { true }

        AlertDialog.Builder(activity!!)
                .setTitle(activity!!.getString(R.string.backup_restore_categories_title, dateLabel))
                .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
                .setPositiveButton(R.string.backup_restore_title) { _, _ ->
                    val selected = categories.filterIndexed { index, _ -> checked[index] }.toSet()
                    if (selected.isNotEmpty()) restoreCategoriesSelectedSubject.onNext(Triple(folder, folderName, selected))
                }
                .setNegativeButton(R.string.button_cancel, null)
                .create()
                .themeButtons(colors.theme().theme)
                .show()
    }

    /** Maps an automatic-backup interval (in days) to its localized summary label. */
    private fun autoBackupFrequencyLabel(days: Int): String = when (days) {
        Preferences.BACKUP_FREQUENCY_NEVER -> activity!!.getString(R.string.backup_auto_never)
        Preferences.BACKUP_FREQUENCY_DAILY -> activity!!.getString(R.string.backup_auto_daily)
        Preferences.BACKUP_FREQUENCY_WEEKLY -> activity!!.getString(R.string.backup_auto_weekly)
        else -> activity!!.resources.getQuantityString(R.plurals.backup_auto_every_days, days, days)
    }

    /** Maps each backup category to its localized checkbox label. */
    private fun categoryLabel(category: BackupCategory): Int = when (category) {
        BackupCategory.MESSAGES -> R.string.backup_category_messages
        BackupCategory.SETTINGS -> R.string.backup_category_settings
        BackupCategory.BLOCKING -> R.string.backup_category_blocking
        BackupCategory.CONVERSATIONS -> R.string.backup_category_conversations
        BackupCategory.SCHEDULED -> R.string.backup_category_scheduled
    }

}
