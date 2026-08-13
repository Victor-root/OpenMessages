package io.openmessages.feature.extensions

import android.content.Context
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import com.bumptech.glide.Glide
import io.openmessages.R
import io.openmessages.extensions.getDefaultActivityIconForMimeType
import io.openmessages.extensions.isAudio
import io.openmessages.extensions.isImage
import io.openmessages.extensions.isVideo
import io.openmessages.extensions.resourceExists

enum class LoadBestIconIntoImageView {
    Missing,
    ImageVideoIcon,
    EmbeddedIcon,
    DefaultAudioIcon,
    ActivityIcon,
    GenericIcon
}

fun Uri.loadBestIconIntoImageView(context: Context, imageView: ImageView)
    : LoadBestIconIntoImageView {

    // if resource is missing, use missing icon
    if (!resourceExists(context)) {
        Glide
            .with(context)
            .load(android.R.drawable.ic_delete)
            .into(imageView)
        return LoadBestIconIntoImageView.Missing
    }

    // if attachment is image or video use image or video frame for icon
    if (isImage(context) || isVideo(context)) {
        Glide
            .with(context)
            .load(this)
            .into(imageView)
        return LoadBestIconIntoImageView.ImageVideoIcon
    }

    var appIcon: Drawable? = null

    var retVal = LoadBestIconIntoImageView.EmbeddedIcon

    // if audio mime type, try and use embedded image (ie. id3 cover art) if one exists
    if (isAudio(context)) {
        MediaMetadataRetriever().apply {
            setDataSource(context, this@loadBestIconIntoImageView)
            val embeddedPicture = embeddedPicture
            if (embeddedPicture != null) {
                Glide
                    .with(context)
                    .load(embeddedPicture)
                    .into(imageView)
                return retVal
            }
        }

        // else, use applications built-in audio icon
        appIcon = AppCompatResources.getDrawable(context, R.drawable.ic_round_volume_up_24)
        retVal = LoadBestIconIntoImageView.DefaultAudioIcon
    }

    // not audio, try and get icon from default activity for type
    if (appIcon == null) {
        appIcon = getDefaultActivityIconForMimeType(context)
        retVal = LoadBestIconIntoImageView.ActivityIcon
    }

    // else, use default local generic attachment icon
    if (appIcon == null) {
        appIcon = AppCompatResources.getDrawable(context, R.drawable.ic_attachment_black_24dp)
        retVal = LoadBestIconIntoImageView.GenericIcon
    }

    // load icon
    Glide
        .with(context)
        .load(appIcon)
        .into(imageView)

    return retVal
}