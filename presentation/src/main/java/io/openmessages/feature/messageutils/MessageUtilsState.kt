package io.openmessages.feature.messageutils

import io.openmessages.repository.MessageRepository

data class MessageUtilsState(
    val autoDeduplicateMessages: Boolean = false,
    val deduplicationProgress: MessageRepository.DeduplicationProgress = MessageRepository.DeduplicationProgress.Idle,

    val autoDelete: Int = 0,
)
