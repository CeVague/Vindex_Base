package com.cevague.vindex.ui.people

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.cevague.vindex.data.database.dao.FaceDao
import java.security.MessageDigest

class FaceCenterCrop(private val face: FaceDao.FaceWithPhoto) : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val width = toTransform.width
        val height = toTransform.height

        // Calculer les coordonnées réelles en pixels (les box sont en normalisé 0..1)
        val left = face.boxLeft * width
        val top = face.boxTop * height
        val right = face.boxRight * width
        val bottom = face.boxBottom * height

        // Centre du visage
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f

        // Taille du carré de crop (on prend la plus grande dimension du visage avec une marge)
        val faceWidth = right - left
        val faceHeight = bottom - top
        val size =
            (maxOf(faceWidth, faceHeight) * 1.5f).coerceAtMost(minOf(width, height).toFloat())

        // Calculer le rectangle de crop centré sur le visage
        val cropRect = RectF(
            centerX - size / 2f,
            centerY - size / 2f,
            centerX + size / 2f,
            centerY + size / 2f
        )

        // Ajuster si on sort de l'image
        if (cropRect.left < 0) cropRect.offset(-cropRect.left, 0f)
        if (cropRect.top < 0) cropRect.offset(0f, -cropRect.top)
        if (cropRect.right > width) cropRect.offset(width - cropRect.right, 0f)
        if (cropRect.bottom > height) cropRect.offset(0f, height - cropRect.bottom)

        // Récupérer un bitmap du pool pour le résultat
        val result = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val srcRect = android.graphics.Rect(
            cropRect.left.toInt(),
            cropRect.top.toInt(),
            cropRect.right.toInt(),
            cropRect.bottom.toInt()
        )
        val destRect = android.graphics.Rect(0, 0, outWidth, outHeight)

        canvas.drawBitmap(toTransform, srcRect, destRect, null)

        return result
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("FaceCenterCrop".toByteArray())
        messageDigest.update(face.id.toString().toByteArray())
    }

    override fun equals(other: Any?): Boolean {
        if (other is FaceCenterCrop) {
            return face.id == other.face.id
        }
        return false
    }

    override fun hashCode(): Int {
        return face.id.hashCode()
    }
}
