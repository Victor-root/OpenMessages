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
package io.openmessages.injection

import dagger.Component
import dagger.android.support.AndroidSupportInjectionModule
import io.openmessages.common.QKApplication
import io.openmessages.common.QkDialog
import io.openmessages.common.util.QkChooserTargetService
import io.openmessages.common.widget.AvatarView
import io.openmessages.common.widget.PagerTitleView
import io.openmessages.common.widget.PreferenceView
import io.openmessages.common.widget.QkEditText
import io.openmessages.common.widget.QkSwitch
import io.openmessages.common.widget.QkTextView
import io.openmessages.common.widget.RadioPreferenceView
import io.openmessages.feature.backup.BackupController
import io.openmessages.feature.blocking.BlockingController
import io.openmessages.feature.blocking.filters.MessageContentFiltersController
import io.openmessages.feature.blocking.manager.BlockingManagerController
import io.openmessages.feature.blocking.messages.BlockedMessagesController
import io.openmessages.feature.blocking.numbers.BlockedNumbersController
import io.openmessages.feature.compose.editing.DetailedChipView
import io.openmessages.feature.conversationinfo.injection.ConversationInfoComponent
import io.openmessages.feature.messageutils.MessageUtilsController
import io.openmessages.feature.settings.SettingsController
import io.openmessages.feature.settings.ThemePickerDialog
import io.openmessages.feature.settings.about.AboutController
import io.openmessages.feature.settings.swipe.SwipeActionsController
import io.openmessages.feature.themepicker.injection.ThemePickerComponent
import io.openmessages.feature.widget.WidgetAdapter
import io.openmessages.injection.android.ActivityBuilderModule
import io.openmessages.injection.android.BroadcastReceiverBuilderModule
import io.openmessages.injection.android.ServiceBuilderModule
import javax.inject.Singleton

@Singleton
@Component(modules = [
    AndroidSupportInjectionModule::class,
    AppModule::class,
    ActivityBuilderModule::class,
    BroadcastReceiverBuilderModule::class,
    ServiceBuilderModule::class])
interface AppComponent {

    fun conversationInfoBuilder(): ConversationInfoComponent.Builder
    fun themePickerBuilder(): ThemePickerComponent.Builder

    fun inject(application: QKApplication)

    fun inject(controller: AboutController)
    fun inject(controller: BackupController)
    fun inject(controller: BlockedMessagesController)
    fun inject(controller: BlockedNumbersController)
    fun inject(controller: MessageContentFiltersController)
    fun inject(controller: BlockingController)
    fun inject(controller: BlockingManagerController)
    fun inject(controller: MessageUtilsController)
    fun inject(controller: SettingsController)
    fun inject(controller: SwipeActionsController)
    fun inject(dialog: ThemePickerDialog)

    fun inject(dialog: QkDialog)

    fun inject(service: WidgetAdapter)

    /**
     * This can't use AndroidInjection, or else it will crash on pre-marshmallow devices
     */
    fun inject(service: QkChooserTargetService)

    fun inject(view: AvatarView)
    fun inject(view: DetailedChipView)
    fun inject(view: PagerTitleView)
    fun inject(view: PreferenceView)
    fun inject(view: RadioPreferenceView)
    fun inject(view: QkEditText)
    fun inject(view: QkSwitch)
    fun inject(view: QkTextView)

}
