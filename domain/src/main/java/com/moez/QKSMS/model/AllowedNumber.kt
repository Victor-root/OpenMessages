package io.openmessages.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allowed_number")
data class AllowedNumber(
        @PrimaryKey var id: Long = 0,

        var address: String = ""
)
