package io.openmessages.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_content_filter")
data class MessageContentFilter(
        @PrimaryKey var id: Long = 0,

        var value: String = "",
        var caseSensitive: Boolean = false,
        var isRegex: Boolean = false,
        var includeContacts: Boolean = false
)

data class MessageContentFilterData(
        var value: String = "",
        var caseSensitive: Boolean = false,
        var isRegex: Boolean = false,
        var includeContacts: Boolean = false
)
