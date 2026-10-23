package io.openmessages.util

import android.net.Uri
import io.openmessages.util.Preference.Converter

class UriPreferenceConverter : Converter<Uri> {

    override fun deserialize(serialized: String): Uri {
        return Uri.parse(serialized)
    }

    override fun serialize(value: Uri): String {
        return value.toString()
    }

}
