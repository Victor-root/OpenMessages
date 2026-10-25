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
import io.openmessages.feature.backup.RestoreBackupService
import io.openmessages.injection.scope.ActivityScope
import io.openmessages.service.HeadlessSmsSendService
import io.openmessages.service.AutoDeleteService

@Module
abstract class ServiceBuilderModule {

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindAutoDeleteService(): AutoDeleteService

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindHeadlessSmsSendService(): HeadlessSmsSendService

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindRestoreBackupService(): RestoreBackupService

}
