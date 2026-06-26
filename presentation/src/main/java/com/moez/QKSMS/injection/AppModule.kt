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
@file:Suppress("DEPRECATION")
package io.openmessages.injection

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.lifecycle.ViewModelProvider
import androidx.work.WorkerFactory
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import io.openmessages.blocking.BlockingClient
import io.openmessages.blocking.BlockingManager
import io.openmessages.common.ViewModelFactory
import io.openmessages.common.util.BillingManagerImpl
import io.openmessages.common.util.NotificationManagerImpl
import io.openmessages.common.util.ShortcutManagerImpl
import io.openmessages.feature.conversationinfo.injection.ConversationInfoComponent
import io.openmessages.feature.themepicker.injection.ThemePickerComponent
import io.openmessages.listener.ContactAddedListener
import io.openmessages.listener.ContactAddedListenerImpl
import io.openmessages.manager.ActiveConversationManager
import io.openmessages.manager.ActiveConversationManagerImpl
import io.openmessages.manager.AlarmManager
import io.openmessages.manager.AlarmManagerImpl
import io.openmessages.manager.BillingManager
import io.openmessages.manager.ChangelogManager
import io.openmessages.manager.ChangelogManagerImpl
import io.openmessages.manager.KeyManager
import io.openmessages.manager.KeyManagerImpl
import io.openmessages.manager.NotificationManager
import io.openmessages.manager.PermissionManager
import io.openmessages.manager.PermissionManagerImpl
import io.openmessages.manager.RatingManager
import io.openmessages.manager.ReferralManager
import io.openmessages.manager.ReferralManagerImpl
import io.openmessages.manager.ShortcutManager
import io.openmessages.manager.WidgetManager
import io.openmessages.manager.WidgetManagerImpl
import io.openmessages.mapper.CursorToContact
import io.openmessages.mapper.CursorToContactGroup
import io.openmessages.mapper.CursorToContactGroupImpl
import io.openmessages.mapper.CursorToContactGroupMember
import io.openmessages.mapper.CursorToContactGroupMemberImpl
import io.openmessages.mapper.CursorToContactImpl
import io.openmessages.mapper.CursorToConversation
import io.openmessages.mapper.CursorToConversationImpl
import io.openmessages.mapper.CursorToMessage
import io.openmessages.mapper.CursorToMessageImpl
import io.openmessages.mapper.CursorToPart
import io.openmessages.mapper.CursorToPartImpl
import io.openmessages.mapper.CursorToRecipient
import io.openmessages.mapper.CursorToRecipientImpl
import io.openmessages.mapper.RatingManagerImpl
import io.openmessages.repository.AllowlistRepository
import io.openmessages.repository.AllowlistRepositoryImpl
import io.openmessages.repository.BackupRepository
import io.openmessages.repository.BackupRepositoryImpl
import io.openmessages.repository.BlockingRepository
import io.openmessages.repository.BlockingRepositoryImpl
import io.openmessages.repository.ContactRepository
import io.openmessages.repository.ContactRepositoryImpl
import io.openmessages.repository.ConversationRepository
import io.openmessages.repository.ConversationRepositoryImpl
import io.openmessages.repository.EmojiReactionRepository
import io.openmessages.repository.EmojiReactionRepositoryImpl
import io.openmessages.repository.MessageContentFilterRepository
import io.openmessages.repository.MessageContentFilterRepositoryImpl
import io.openmessages.repository.MessageRepository
import io.openmessages.repository.MessageRepositoryImpl
import io.openmessages.repository.ScheduledMessageRepository
import io.openmessages.repository.ScheduledMessageRepositoryImpl
import io.openmessages.repository.SyncRepository
import io.openmessages.repository.SyncRepositoryImpl
import io.openmessages.worker.InjectionWorkerFactory
import javax.inject.Singleton

@Module(subcomponents = [
    ConversationInfoComponent::class,
    ThemePickerComponent::class])
class AppModule(private var application: Application) {

    @Provides
    @Singleton
    fun provideContext(): Context = application

    @Provides
    fun provideContentResolver(context: Context): ContentResolver = context.contentResolver

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    fun provideRxPreferences(preferences: SharedPreferences): RxSharedPreferences {
        return RxSharedPreferences.create(preferences)
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
    }

    @Provides
    fun provideViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory = factory

    // Listener

    @Provides
    fun provideContactAddedListener(listener: ContactAddedListenerImpl): ContactAddedListener = listener

    // Manager

    @Provides
    fun provideBillingManager(manager: BillingManagerImpl): BillingManager = manager

    @Provides
    fun provideActiveConversationManager(manager: ActiveConversationManagerImpl): ActiveConversationManager = manager

    @Provides
    fun provideAlarmManager(manager: AlarmManagerImpl): AlarmManager = manager

    @Provides
    fun blockingClient(manager: BlockingManager): BlockingClient = manager

    @Provides
    fun changelogManager(manager: ChangelogManagerImpl): ChangelogManager = manager

    @Provides
    fun provideKeyManager(manager: KeyManagerImpl): KeyManager = manager

    @Provides
    fun provideNotificationsManager(manager: NotificationManagerImpl): NotificationManager = manager

    @Provides
    fun providePermissionsManager(manager: PermissionManagerImpl): PermissionManager = manager

    @Provides
    fun provideRatingManager(manager: RatingManagerImpl): RatingManager = manager

    @Provides
    fun provideShortcutManager(manager: ShortcutManagerImpl): ShortcutManager = manager

    @Provides
    fun provideReferralManager(manager: ReferralManagerImpl): ReferralManager = manager

    @Provides
    fun provideWidgetManager(manager: WidgetManagerImpl): WidgetManager = manager

    // Mapper

    @Provides
    fun provideCursorToContact(mapper: CursorToContactImpl): CursorToContact = mapper

    @Provides
    fun provideCursorToContactGroup(mapper: CursorToContactGroupImpl): CursorToContactGroup = mapper

    @Provides
    fun provideCursorToContactGroupMember(mapper: CursorToContactGroupMemberImpl): CursorToContactGroupMember = mapper

    @Provides
    fun provideCursorToConversation(mapper: CursorToConversationImpl): CursorToConversation = mapper

    @Provides
    fun provideCursorToMessage(mapper: CursorToMessageImpl): CursorToMessage = mapper

    @Provides
    fun provideCursorToPart(mapper: CursorToPartImpl): CursorToPart = mapper

    @Provides
    fun provideCursorToRecipient(mapper: CursorToRecipientImpl): CursorToRecipient = mapper

    // Repository

    @Provides
    fun provideBackupRepository(repository: BackupRepositoryImpl): BackupRepository = repository

    @Provides
    fun provideBlockingRepository(repository: BlockingRepositoryImpl): BlockingRepository = repository

    @Provides
    fun provideAllowlistRepository(repository: AllowlistRepositoryImpl): AllowlistRepository = repository

    @Provides
    fun provideMessageContentFilterRepository(repository: MessageContentFilterRepositoryImpl): MessageContentFilterRepository = repository

    @Provides
    fun provideContactRepository(repository: ContactRepositoryImpl): ContactRepository = repository

    @Provides
    fun provideConversationRepository(repository: ConversationRepositoryImpl): ConversationRepository = repository

    @Provides
    fun provideMessageRepository(repository: MessageRepositoryImpl): MessageRepository = repository

    @Provides
    fun provideScheduledMessagesRepository(repository: ScheduledMessageRepositoryImpl): ScheduledMessageRepository = repository

    @Provides
    fun provideSyncRepository(repository: SyncRepositoryImpl): SyncRepository = repository

    @Provides
    fun provideEmojiReactionRepository(repository: EmojiReactionRepositoryImpl): EmojiReactionRepository = repository

    // worker factory
    @Provides
    fun provideWorkerFactory(workerFactory: InjectionWorkerFactory): WorkerFactory = workerFactory
}