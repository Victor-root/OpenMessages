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

import android.content.Intent
import io.openmessages.common.base.QkView
import io.openmessages.manager.ChangelogManager
import io.reactivex.Observable

interface MainView : QkView<MainState> {

    val onNewIntentIntent: Observable<Intent>
    val activityResumedIntent: Observable<Boolean>
    val queryChangedIntent: Observable<CharSequence>
    val composeIntent: Observable<Unit>
    val drawerToggledIntent: Observable<Boolean>
    val homeIntent: Observable<*>
    val navigationIntent: Observable<NavItem>
    val optionsItemIntent: Observable<Int>
//    val plusBannerIntent: Observable<*>
    val dismissRatingIntent: Observable<*>
    val rateIntent: Observable<*>
    val conversationsSelectedIntent: Observable<List<Long>>
    val confirmDeleteIntent: Observable<List<Long>>
    /** The dialog the user closed, so a dialog opened in its place is not closed along with it. */
    val dialogDismissedIntent: Observable<MainDialog>
    /** The conversation to rename and the name typed for it. */
    val renameConversationIntent: Observable<Pair<Long, String>>
    val swipeConversationIntent: Observable<Pair<Long, Int>>
    val changelogMoreIntent: Observable<*>
    val undoArchiveIntent: Observable<Unit>
    val snackbarButtonIntent: Observable<Unit>

    fun requestDefaultSms()
    fun requestPermissions()
    fun shouldShowNotificationRationale(): Boolean
    fun clearSearch()
    fun clearSelection()
    fun toggleSelectAll()
    fun themeChanged()
    fun showBlockingDialog(conversations: List<Long>, block: Boolean)
    fun showChangelog(changelog: ChangelogManager.CumulativeChangelog)
    fun showArchivedSnackbar(countConversationsArchived: Int, isArchiving: Boolean)
    fun drawerToggled(opened: Boolean)
}

enum class NavItem { BACK, INBOX, ARCHIVED, BACKUP, SCHEDULED, BLOCKING, MESSAGE_UTILS, TEMPLATES, SETTINGS, ABOUT, PLUS, HELP, INVITE }
