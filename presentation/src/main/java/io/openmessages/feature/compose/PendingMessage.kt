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
package io.openmessages.feature.compose

import io.openmessages.model.Message

/**
 * Whether this message, read back from the provider, is the one [pending] stood for: the SMS shown
 * from the moment it was sent until it was written, see MessageRepository.outgoingSmsPreview.
 * A pending message has a negative id, which no stored message has.
 */
fun Message.settles(pending: Message): Boolean = pending.id < 0 && id >= 0 &&
        isSms() && isMe() && threadId == pending.threadId && body == pending.body && date >= pending.date
