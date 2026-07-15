package com.cevague.vindex.ui.people

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.cevague.vindex.data.database.dao.FaceDao
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.max

/**
 * Découpe un visage dans sa photo, en [outputSize] pixels de côté.
 *
 * [outputSize] est explicite et **ne suit pas** la taille demandée à Glide, parce
 * que les deux ne veulent pas dire la même chose : Glide dimensionne le décodage de
 * la photo **entière**, alors qu'on ne garde qu'un carré autour du visage. Les
 * confondre — ce que fait le réglage par défaut — revient à décoder la photo à la
 * taille de la vignette, si bien qu'un visage occupant 10 % de la largeur n'a plus
 * que quelques dizaines de pixels avant d'être ré-étiré. D'où [sourceSizeFor], qui
 * fait le chemin inverse : de la taille voulue **pour le visage** vers la taille à
 * décoder **pour la photo**.
 *
 * Le cercle n'est pas fait ici : les vues sont des `ShapeableImageView` en
 * `Corner.Full`, elles découpent déjà. Un `CircleCrop` de Glide en plus
 * re-dimensionnerait la sortie à la taille demandée et annulerait tout le bénéfice.
 */
class FaceCropTransformation(
    private val face: FaceDao.FaceWithPhoto,
    private val outputSize: Int
) : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val width = toTransform.width
        val height = toTransform.height

        // Les boîtes sont normalisées 0..1 : on repasse en pixels du bitmap décodé.
        val left = face.boxLeft * width
        val top = face.boxTop * height
        val right = face.boxRight * width
        val bottom = face.boxBottom * height

        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f

        // Carré autour du visage, avec de la marge : un visage collé à sa boîte est
        // désagréable à regarder.
        val size = (max(right - left, bottom - top) * MARGIN)
            .coerceAtMost(minOf(width, height).toFloat())

        val cropRect = RectF(
            centerX - size / 2f,
            centerY - size / 2f,
            centerX + size / 2f,
            centerY + size / 2f
        )

        // Recentrage si la marge déborde de l'image.
        if (cropRect.left < 0) cropRect.offset(-cropRect.left, 0f)
        if (cropRect.top < 0) cropRect.offset(0f, -cropRect.top)
        if (cropRect.right > width) cropRect.offset(width - cropRect.right, 0f)
        if (cropRect.bottom > height) cropRect.offset(0f, height - cropRect.bottom)

        val result = pool.get(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val srcRect = Rect(
            cropRect.left.toInt(),
            cropRect.top.toInt(),
            cropRect.right.toInt(),
            cropRect.bottom.toInt()
        )
        val destRect = Rect(0, 0, outputSize, outputSize)

        // Sans Paint filtrant, `drawBitmap` échantillonne au plus proche voisin : le
        // visage sort en blocs francs, pas en flou — c'est ce qui se voyait le plus.
        Canvas(result).drawBitmap(toTransform, srcRect, destRect, FILTER)
        return result
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("FaceCenterCrop".toByteArray())
        messageDigest.update(face.id.toString().toByteArray())
        messageDigest.update(outputSize.toString().toByteArray())
    }

    override fun equals(other: Any?): Boolean =
        other is FaceCropTransformation && face.id == other.face.id && outputSize == other.outputSize

    override fun hashCode(): Int = 31 * face.id.hashCode() + outputSize

    companion object {
        /** Marge autour de la boîte, en multiple de son plus grand côté. */
        private const val MARGIN = 1.5f

        private val FILTER = Paint(Paint.FILTER_BITMAP_FLAG)

        /**
         * Plafond du décodage. Un visage plus petit que ~9 % de la photo restera un
         * peu mou en vignette : c'est assumé, la seule alternative serait de décoder
         * plusieurs mégapixels **par tuile** d'une grille qui en affiche vingt.
         */
        private const val MAX_SOURCE = 2048

        /**
         * Taille à demander à Glide (`override`) pour que le visage fasse au moins
         * [outputSize] pixels une fois découpé.
         *
         * Approximation assumée : la boîte est normalisée par axe alors qu'on ne
         * demande qu'un carré à Glide. L'erreur vaut le ratio de la photo, et se paie
         * en pixels décodés en trop — jamais en netteté.
         */
        fun sourceSizeFor(face: FaceDao.FaceWithPhoto, outputSize: Int): Int {
            val span = max(face.boxRight - face.boxLeft, face.boxBottom - face.boxTop) * MARGIN
            if (span <= 0f) return outputSize
            return ceil(outputSize / span).toInt().coerceIn(outputSize, MAX_SOURCE)
        }
    }
}
