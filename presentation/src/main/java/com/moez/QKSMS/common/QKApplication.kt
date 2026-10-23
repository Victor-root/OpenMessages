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
package io.openmessages.common

import android.app.Application
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import io.openmessages.R
import io.openmessages.common.util.FileLoggingTree
import io.openmessages.injection.AppComponentManager
import io.openmessages.injection.appComponent
import io.openmessages.interactor.SpeakThreads
import io.openmessages.migration.QkMigration
import io.openmessages.util.NightModeManager
import io.openmessages.util.Preferences
import io.openmessages.worker.AutoBackupWorker
import io.openmessages.worker.HousekeepingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import rxdogtag2.RxDogTag
import rxdogtag2.autodispose2.AutoDisposeConfigurer
import timber.log.Timber
import javax.inject.Inject

class QKApplication : Application(), HasAndroidInjector {

    @Inject lateinit var qkMigration: QkMigration

    @Inject lateinit var androidInjector: DispatchingAndroidInjector<Any>
    @Inject lateinit var fileLoggingTree: FileLoggingTree
    @Inject lateinit var nightModeManager: NightModeManager
    @Inject lateinit var prefs: Preferences
    @Inject lateinit var workerFactory: WorkerFactory

    override fun onCreate() {
        super.onCreate()

        // set translated "no messages" string for speakThreads interactor
        SpeakThreads.setNoMessagesString(getString(R.string.speak_no_messages))

        AppComponentManager.init(this)
        appComponent.inject(this)

        qkMigration.performMigration()

        nightModeManager.updateCurrentTheme()

        // configure timber logging
        Timber.plant(Timber.DebugTree(), fileLoggingTree)

        // configure emoji compatibility with bundled package
        // (bundled library works with no play-services/gsm os versions)
        EmojiCompat.init(BundledEmojiCompatConfig(this, Dispatchers.IO.asExecutor())
            .registerInitCallback(object: EmojiCompat.InitCallback() {
                override fun onInitialized() {
                    super.onInitialized()
                    Timber.v("bundled emojicompat initialized")
                }

                override fun onFailed(throwable: Throwable?) {
                    super.onFailed(throwable)
                    Timber.e("bundled emojicompat initialization failed")
                }
            })
        )

        // rxdogtag provides 'look-back' for exceptions in rxjava 'chains'
        RxDogTag.builder()
                .configureWith(AutoDisposeConfigurer::configure)
                .install()

        // init work manager with custom factory supporting dagger/injection capability
        WorkManager.initialize(
            this,
            Configuration.Builder().setWorkerFactory(workerFactory).build()
        )

        // register, or re-register, housekeeping work manager
        HousekeepingWorker.register(applicationContext)

        // register, or re-register, the automatic backup according to the saved frequency
        AutoBackupWorker.register(applicationContext, prefs.backupFrequency.get())
    }

    override fun androidInjector(): AndroidInjector<Any> = androidInjector

}