package io.openmessages.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_number")
data class BlockedNumber(
        @PrimaryKey var id: Long = 0,

        var address: String = ""
)
