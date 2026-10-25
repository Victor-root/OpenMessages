/*
 * Copyright (C) 2026 OpenMessages contributors
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

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.openmessages.repository.BackupRepositoryImpl.Backup
import io.openmessages.repository.BackupRepositoryImpl.BackupManifest
import io.openmessages.repository.BackupRepositoryImpl.BackupMessage
import io.openmessages.repository.BackupRepositoryImpl.BackupMmsMessage
import io.openmessages.repository.BackupRepositoryImpl.BackupMmsPart
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Backups are written and read by Moshi through Kotlin reflection, set up as in [AppModule]
 */
class BackupJsonTest {

    private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

    @Test
    fun backup_roundTrips() {
        val backup = Backup(
            messageCount = 1,
            messages = listOf(BackupMessage(
                type = 1,
                address = "+33612345678",
                date = 1_700_000_000_000,
                dateSent = 1_700_000_000_000,
                read = true,
                status = -1,
                body = "Hello",
                protocol = 0,
                serviceCenter = null,
                locked = false,
                subId = 1
            )),
            mmsCount = 1,
            mms = listOf(BackupMmsMessage(
                subject = "Photo",
                recipients = listOf("+33612345678"),
                parts = listOf(BackupMmsPart(type = "image/jpeg", seq = 0, dataFile = "1.jpg"))
            ))
        )
        val adapter = moshi.adapter(Backup::class.java)

        assertEquals(backup, adapter.fromJson(adapter.toJson(backup)))
    }

    @Test
    fun manifest_missingFieldsTakeTheirDefaults() {
        val manifest = moshi.adapter(BackupManifest::class.java).fromJson("""{"version":3}""")

        assertEquals(BackupManifest(version = 3), manifest)
    }

}
