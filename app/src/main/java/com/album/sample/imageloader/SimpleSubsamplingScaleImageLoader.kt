package com.album.sample.imageloader

import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import com.album.R
import com.album.core.scan.AlbumEntity
import com.album.core.scan.FinderEntity
import com.album.listener.AlbumImageLoader
import com.album.widget.AlbumImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

class SimpleSubsamplingScaleImageLoader : AlbumImageLoader {

    private val requestOptions: RequestOptions = RequestOptions().placeholder(R.drawable.ic_album_default_loading).error(R.drawable.ic_album_default_loading).centerCrop()

    override fun displayAlbum(width: Int, height: Int, albumEntity: AlbumEntity, container: FrameLayout): View {
        val albumImageView = AlbumImageView(container.context)
        Glide.with(container.context).load(albumEntity.path).apply(requestOptions).into(albumImageView)
        return albumImageView
    }

    override fun displayAlbumThumbnails(finderEntity: FinderEntity, container: FrameLayout): View {
        val albumImageView = AlbumImageView(container.context)
        Glide.with(container.context).load(finderEntity.thumbnailsPath).apply(requestOptions).into(albumImageView)
        return albumImageView
    }

    override fun displayPreview(albumEntity: AlbumEntity, container: FrameLayout): View {
        val subsamplingScaleImageView = SubsamplingScaleImageView(container.context)
        Glide.with(subsamplingScaleImageView).asBitmap().load(albumEntity.path).apply(requestOptions).into(object : SimpleTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                subsamplingScaleImageView.setImage(ImageSource.cachedBitmap(resource))
            }
        })
        return subsamplingScaleImageView
    }
}
