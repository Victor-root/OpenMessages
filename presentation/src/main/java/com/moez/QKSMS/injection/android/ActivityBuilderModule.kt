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
package io.openmessages.injection.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import io.openmessages.feature.backup.BackupActivity
import io.openmessages.feature.blocking.BlockingActivity
import io.openmessages.feature.compose.ComposeActivity
import io.openmessages.feature.compose.ComposeActivityModule
import io.openmessages.feature.contacts.ContactsActivity
import io.openmessages.feature.contacts.ContactsActivityModule
import io.openmessages.feature.conversationinfo.ConversationInfoActivity
import io.openmessages.feature.gallery.GalleryActivity
import io.openmessages.feature.gallery.GalleryActivityModule
import io.openmessages.feature.main.MainActivity
import io.openmessages.feature.main.MainActivityModule
import io.openmessages.feature.messageutils.MessageUtilsActivity
import io.openmessages.feature.notificationprefs.NotificationPrefsActivity
import io.openmessages.feature.notificationprefs.NotificationPrefsActivityModule
import io.openmessages.feature.plus.PlusActivity
import io.openmessages.feature.plus.PlusActivityModule
import io.openmessages.feature.qkreply.QkReplyActivity
import io.openmessages.feature.qkreply.QkReplyActivityModule
import io.openmessages.feature.scheduled.ScheduledActivity
import io.openmessages.feature.scheduled.ScheduledActivityModule
import io.openmessages.feature.settings.SettingsActivity
import io.openmessages.feature.settings.about.AboutActivity
import io.openmessages.feature.templates.TemplatesActivity
import io.openmessages.feature.templates.TemplatesActivityModule
import io.openmessages.injection.scope.ActivityScope

@Module
abstract class ActivityBuilderModule {

    @ActivityScope
    @ContributesAndroidInjector(modules = [MainActivityModule::class])
    abstract fun bindMainActivity(): MainActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [PlusActivityModule::class])
    abstract fun bindPlusActivity(): PlusActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindBackupActivity(): BackupActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ComposeActivityModule::class])
    abstract fun bindComposeActivity(): ComposeActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ContactsActivityModule::class])
    abstract fun bindContactsActivity(): ContactsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindConversationInfoActivity(): ConversationInfoActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [GalleryActivityModule::class])
    abstract fun bindGalleryActivity(): GalleryActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [NotificationPrefsActivityModule::class])
    abstract fun bindNotificationPrefsActivity(): NotificationPrefsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [QkReplyActivityModule::class])
    abstract fun bindQkReplyActivity(): QkReplyActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ScheduledActivityModule::class])
    abstract fun bindScheduledActivity(): ScheduledActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [TemplatesActivityModule::class])
    abstract fun bindTemplatesActivity(): TemplatesActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindMessageUtilsActivity(): MessageUtilsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindSettingsActivity(): SettingsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindAboutActivity(): AboutActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindBlockingActivity(): BlockingActivity

}
