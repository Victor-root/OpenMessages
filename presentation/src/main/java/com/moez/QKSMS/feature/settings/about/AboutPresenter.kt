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
package io.openmessages.feature.settings.about

import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.base.QkPresenter
import javax.inject.Inject

class AboutPresenter @Inject constructor(
    private val navigator: Navigator
) : QkPresenter<AboutView, Unit>(Unit) {

    override fun bindIntents(view: AboutView) {
        super.bindIntents(view)

        view.preferenceClicks()
                .autoDisposable(view.scope())
                .subscribe { preference ->
                    when (preference.id) {
                        R.id.developer -> navigator.showDeveloper()

                        R.id.source -> navigator.showSourceCode()

                        R.id.changelog -> navigator.showChangelog()

                        R.id.contact -> navigator.showSupport()

                        R.id.license -> navigator.showLicense()
                    }
                }
    }

}