package io.openmessages.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class AllowedNumber(
        @PrimaryKey var id: Long = 0,

        var address: String = ""
) : RealmObject()
