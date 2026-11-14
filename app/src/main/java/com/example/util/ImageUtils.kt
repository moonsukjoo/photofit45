package com.example.util

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.FileProvider
import com.example.model.ExifDetails
import com.example.model.FilterPreset
import com.example.model.ImageAdjustment
import com.example.model.ImageFormat
import com.example.model.ResizeMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ImageMetadata(
    val name: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String
)

object ImageUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val df = DecimalFormat("#,##0.#")
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> "${df.format(gb)} GB"
            mb >= 1.0 -> "${df.format(mb)} MB"
            kb >= 1.0 -> "${df.format(kb)} KB"
            else -> "$bytes B"
        }
    }

    fun getImageMetadata(context: Context, uri: Uri): ImageMetadata {
        var name = "image_${System.currentTimeMillis()}"
        var size: Long = 0
        var mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

        // Query file name and size from content resolver
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback for size if cursor didn't return it
        if (size <= 0) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    size = input.available().toLong()
                }
            } catch (_: Exception) {}
        }

        // Query image dimensions without loading full bitmap into memory
        var width = 0
        var height = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input, null, options)
                width = options.outWidth
                height = options.outHeight
                if (options.outMimeType != null) {
                    mimeType = options.outMimeType
                }
            }
        } catch (_: Exception) {}

        // Check EXIF orientation
        val orientation = getExifOrientation(context, uri)
        if (orientation == 90 || orientation == 270) {
            val temp = width
            width = height
            height = temp
        }

        return ImageMetadata(
            name = name,
            sizeBytes = size,
            width = max(1, width),
            height = max(1, height),
            mimeType = mimeType
        )
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                return when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (_: Exception) {}
        return 0
    }

    fun loadBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap? {
        try {
            // First decode bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight: Int = options.outHeight / 2
                val halfWidth: Int = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension &&
                    (halfWidth / inSampleSize) >= maxDimension
                ) {
                    inSampleSize *= 2
                }
            }

            // Decode bitmap with inSampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            var bitmap: Bitmap? = null
            context.contentResolver.openInputStream(uri)?.use { input ->
                bitmap = BitmapFactory.decodeStream(input, null, decodeOptions)
            }

            if (bitmap == null) return null

            // Correct EXIF orientation
            val orientation = getExifOrientation(context, uri)
            if (orientation != 0) {
                val matrix = Matrix().apply { postRotate(orientation.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true
                )
                if (rotated != bitmap) {
                    bitmap!!.recycle()
                    bitmap = rotated
                }
            }
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun applyAdjustments(
        source: Bitmap,
        adjustment: ImageAdjustment
    ): Bitmap {
        if (adjustment.isDefault) {
            return source
        }

        // ColorMatrix chaining
        val finalColorMatrix = ColorMatrix()

        // 1. Preset filter matrix
        val presetMatrix = getPresetColorMatrix(adjustment.filterPreset)
        if (presetMatrix != null) {
            finalColorMatrix.postConcat(presetMatrix)
        }

        // 2. Saturation
        if (adjustment.saturation != 0f) {
            val satMatrix = ColorMatrix()
            val satValue = 1f + (adjustment.saturation / 100f)
            satMatrix.setSaturation(satValue.coerceIn(0f, 3f))
            finalColorMatrix.postConcat(satMatrix)
        }

        // 3. Brightness & Contrast
        if (adjustment.brightness != 0f || adjustment.contrast != 0f) {
            val bcMatrix = ColorMatrix()
            val contrastFactor = 1f + (adjustment.contrast / 100f)
            val brightnessAdd = adjustment.brightness * 1.5f // -150 .. +150

            val scale = contrastFactor.coerceIn(0f, 3f)
            val translate = (1f - scale) * 128f + brightnessAdd

            val array = floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
            bcMatrix.set(array)
            finalColorMatrix.postConcat(bcMatrix)
        }

        // 4. Warmth (Red boost / Blue reduction)
        if (adjustment.warmth != 0f) {
            val warmthMatrix = ColorMatrix()
            val warmthNorm = adjustment.warmth / 100f // -1.0 .. +1.0
            val rScale = if (warmthNorm > 0) 1f + warmthNorm * 0.2f else 1f
            val gScale = 1f + warmthNorm * 0.05f
            val bScale = if (warmthNorm < 0) 1f + (-warmthNorm) * 0.25f else 1f - warmthNorm * 0.15f

            val array = floatArrayOf(
                rScale, 0f, 0f, 0f, 0f,
                0f, gScale, 0f, 0f, 0f,
                0f, 0f, bScale, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            warmthMatrix.set(array)
            finalColorMatrix.postConcat(warmthMatrix)
        }

        // Draw onto filtered bitmap
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(finalColorMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        // 5. Vignette overlay if requested
        if (adjustment.vignette > 0f) {
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val centerX = source.width / 2f
            val centerY = source.height / 2f
            val radius = max(centerX, centerY) * 1.2f
            val alpha = ((adjustment.vignette / 100f) * 220).roundToInt().coerceIn(0, 255)

            val colors = intArrayOf(
                Color.TRANSPARENT,
                Color.argb(alpha / 4, 0, 0, 0),
                Color.argb(alpha, 0, 0, 0)
            )
            val stops = floatArrayOf(0.4f, 0.75f, 1.0f)
            vignettePaint.shader = RadialGradient(
                centerX, centerY, radius, colors, stops, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), vignettePaint)
        }

        // 6. Rotation and Flip
        if (adjustment.rotation != 0 || adjustment.flipHorizontal) {
            val matrix = Matrix()
            if (adjustment.flipHorizontal) {
                matrix.postScale(-1f, 1f, result.width / 2f, result.height / 2f)
            }
            if (adjustment.rotation != 0) {
                matrix.postRotate(adjustment.rotation.toFloat(), result.width / 2f, result.height / 2f)
            }
            val transformed = Bitmap.createBitmap(
                result, 0, 0, result.width, result.height, matrix, true
            )
            if (transformed != result) {
                result.recycle()
                return transformed
            }
        }

        return result
    }

    private fun getPresetColorMatrix(preset: FilterPreset): ColorMatrix? {
        val matrix = ColorMatrix()
        return when (preset) {
            FilterPreset.ORIGINAL -> null
            FilterPreset.VIVID -> {
                // High saturation and slight contrast boost
                val sat = ColorMatrix().apply { setSaturation(1.4f) }
                val con = ColorMatrix(
                    floatArrayOf(
                        1.15f, 0f, 0f, 0f, -15f,
                        0f, 1.15f, 0f, 0f, -15f,
                        0f, 0f, 1.15f, 0f, -15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                matrix.postConcat(sat)
                matrix.postConcat(con)
                matrix
            }
            FilterPreset.WARM -> {
                // Golden warm tone
                val array = floatArrayOf(
                    1.18f, 0f, 0f, 0f, 10f,
                    0f, 1.05f, 0f, 0f, 5f,
                    0f, 0f, 0.88f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
                ColorMatrix(array)
            }
            FilterPreset.COOL -> {
                // Crisp cool tone
                val array = floatArrayOf(
                    0.90f, 0f, 0f, 0f, -5f,
                    0f, 0.98f, 0f, 0f, 0f,
                    0f, 0f, 1.22f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                )
                ColorMatrix(array)
            }
            FilterPreset.MONO -> {
                // Monochrome classic
                matrix.setSaturation(0f)
                val con = ColorMatrix(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, -20f,
                        0f, 1.2f, 0f, 0f, -20f,
                        0f, 0f, 1.2f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                matrix.postConcat(con)
                matrix
            }
            FilterPreset.SEPIA -> {
                // Vintage sepia
                val array = floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                ColorMatrix(array)
            }
            FilterPreset.DRAMATIC -> {
                // High contrast, moody shadows
                val con = ColorMatrix(
                    floatArrayOf(
                        1.45f, 0f, 0f, 0f, -45f,
                        0f, 1.45f, 0f, 0f, -45f,
                        0f, 0f, 1.45f, 0f, -45f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                val sat = ColorMatrix().apply { setSaturation(1.1f) }
                matrix.postConcat(con)
                matrix.postConcat(sat)
                matrix
            }
            FilterPreset.SOFT -> {
                // Soft muted dreamy tone
                val con = ColorMatrix(
                    floatArrayOf(
                        0.92f, 0f, 0f, 0f, 25f,
                        0f, 0.92f, 0f, 0f, 25f,
                        0f, 0f, 0.92f, 0f, 25f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                val sat = ColorMatrix().apply { setSaturation(0.85f) }
                matrix.postConcat(con)
                matrix.postConcat(sat)
                matrix
            }
            FilterPreset.CYBER -> {
                // Cyberpunk / Neon cyan and magenta boost
                val array = floatArrayOf(
                    1.2f, 0f, 0.2f, 0f, 10f,
                    0f, 0.9f, 0.1f, 0f, -5f,
                    0.2f, 0.2f, 1.35f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
                )
                ColorMatrix(array)
            }
        }
    }

    fun calculateTargetDimensions(
        origWidth: Int,
        origHeight: Int,
        resizeMode: ResizeMode,
        customWidth: Int = 0,
        customHeight: Int = 0
    ): Pair<Int, Int> {
        if (origWidth <= 0 || origHeight <= 0) return Pair(100, 100)

        return when (resizeMode) {
            ResizeMode.ORIGINAL -> Pair(origWidth, origHeight)
            ResizeMode.PERCENT_75 -> Pair(
                max(1, (origWidth * 0.75f).roundToInt()),
                max(1, (origHeight * 0.75f).roundToInt())
            )
            ResizeMode.PERCENT_50 -> Pair(
                max(1, (origWidth * 0.50f).roundToInt()),
                max(1, (origHeight * 0.50f).roundToInt())
            )
            ResizeMode.PERCENT_25 -> Pair(
                max(1, (origWidth * 0.25f).roundToInt()),
                max(1, (origHeight * 0.25f).roundToInt())
            )
            ResizeMode.FHD_1080P -> scaleToMaxDimension(origWidth, origHeight, 1920)
            ResizeMode.HD_720P -> scaleToMaxDimension(origWidth, origHeight, 1280)
            ResizeMode.INSTA_FEED -> scaleToMaxDimension(origWidth, origHeight, 1080)
            ResizeMode.KAKAO_PROFILE -> scaleToMaxDimension(origWidth, origHeight, 640)
            ResizeMode.WEB_THUMB -> scaleToMaxDimension(origWidth, origHeight, 500)
            ResizeMode.CUSTOM -> {
                val w = if (customWidth > 0) customWidth else origWidth
                val h = if (customHeight > 0) customHeight else origHeight
                Pair(max(1, w), max(1, h))
            }
        }
    }

    private fun scaleToMaxDimension(origW: Int, origH: Int, maxDim: Int): Pair<Int, Int> {
        val currentMax = max(origW, origH)
        if (currentMax <= maxDim) {
            return Pair(origW, origH)
        }
        val factor = maxDim.toFloat() / currentMax.toFloat()
        return Pair(
            max(1, (origW * factor).roundToInt()),
            max(1, (origH * factor).roundToInt())
        )
    }

    fun resizeBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) {
            return source
        }
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    fun compressBitmap(
        bitmap: Bitmap,
        format: ImageFormat,
        quality: Int
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        val compressFormat = format.toCompressFormat()
        val effectiveQuality = if (format == ImageFormat.PNG) 100 else quality.coerceIn(1, 100)
        bitmap.compress(compressFormat, effectiveQuality, stream)
        return stream.toByteArray()
    }

    fun compressToTargetSize(
        bitmap: Bitmap,
        format: ImageFormat,
        targetMaxBytes: Long
    ): Pair<ByteArray, Int> {
        var currentBitmap = bitmap
        var quality = 90
        var bytes = compressBitmap(currentBitmap, format, quality)

        if (bytes.size <= targetMaxBytes) {
            return Pair(bytes, quality)
        }

        // 1. Lower quality iteratively
        while (quality > 25 && bytes.size > targetMaxBytes) {
            quality -= 15
            bytes = compressBitmap(currentBitmap, format, quality)
        }

        // 2. If still exceeds target size, downscale dimension
        while (bytes.size > targetMaxBytes && currentBitmap.width > 300 && currentBitmap.height > 300) {
            val newW = (currentBitmap.width * 0.8f).roundToInt()
            val newH = (currentBitmap.height * 0.8f).roundToInt()
            val scaled = Bitmap.createScaledBitmap(currentBitmap, newW, newH, true)
            if (currentBitmap != bitmap) {
                currentBitmap.recycle()
            }
            currentBitmap = scaled
            bytes = compressBitmap(currentBitmap, format, quality)
        }

        if (currentBitmap != bitmap) {
            currentBitmap.recycle()
        }

        return Pair(bytes, quality)
    }

    fun saveBitmapToGallery(
        context: Context,
        bytes: ByteArray,
        baseName: String,
        format: ImageFormat,
        subFolder: String = "PhotoFit"
    ): Uri? {
        val resolver: ContentResolver = context.contentResolver
        val cleanName = baseName.substringBeforeLast(".")
        val fileName = "${cleanName}_edited_${System.currentTimeMillis()}.${format.extension}"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$subFolder")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
                stream.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            return uri
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {}
            return null
        }
    }

    fun saveToCacheForSharing(
        context: Context,
        bytes: ByteArray,
        baseName: String,
        format: ImageFormat
    ): Uri? {
        try {
            val cacheFolder = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val cleanName = baseName.substringBeforeLast(".")
            val file = File(cacheFolder, "${cleanName}_share_${System.currentTimeMillis()}.${format.extension}")
            FileOutputStream(file).use { out ->
                out.write(bytes)
                out.flush()
            }
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun createDeleteIntentRequest(
        context: Context,
        uris: List<Uri>
    ): IntentSenderRequest? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent: PendingIntent =
                    MediaStore.createDeleteRequest(context.contentResolver, uris)
                return IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    fun deleteUrisDirectly(context: Context, uris: List<Uri>): Int {
        var count = 0
        for (uri in uris) {
            try {
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) count++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return count
    }

    data class DevicePhotoEntry(
        val uri: Uri,
        val name: String,
        val sizeBytes: Long,
        val width: Int,
        val height: Int,
        val mimeType: String
    )

    fun queryDevicePhotos(context: Context, limit: Int = 500): List<Uri> {
        return queryDevicePhotosWithMetadata(context, limit).map { it.uri }
    }

    fun queryDevicePhotosWithMetadata(context: Context, limit: Int = 500): List<DevicePhotoEntry> {
        val items = mutableListOf<DevicePhotoEntry>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.ORIENTATION
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val orientCol = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)

                while (cursor.moveToNext() && items.size < limit) {
                    val id = cursor.getLong(idCol)
                    val contentUri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val name = if (nameCol != -1 && !cursor.isNull(nameCol)) {
                        cursor.getString(nameCol)
                    } else {
                        "image_$id.jpg"
                    }
                    val size = if (sizeCol != -1 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                    var width = if (widthCol != -1 && !cursor.isNull(widthCol)) cursor.getInt(widthCol) else 0
                    var height = if (heightCol != -1 && !cursor.isNull(heightCol)) cursor.getInt(heightCol) else 0
                    val orientation = if (orientCol != -1 && !cursor.isNull(orientCol)) cursor.getInt(orientCol) else 0
                    val mime = if (mimeCol != -1 && !cursor.isNull(mimeCol)) {
                        cursor.getString(mimeCol)
                    } else {
                        "image/jpeg"
                    }

                    if (orientation == 90 || orientation == 270) {
                        val tmp = width
                        width = height
                        height = tmp
                    }

                    if (width <= 0 || height <= 0 || size <= 0L) {
                        val fallback = getImageMetadata(context, contentUri)
                        items.add(
                            DevicePhotoEntry(
                                uri = contentUri,
                                name = fallback.name,
                                sizeBytes = fallback.sizeBytes,
                                width = fallback.width,
                                height = fallback.height,
                                mimeType = fallback.mimeType
                            )
                        )
                    } else {
                        items.add(
                            DevicePhotoEntry(
                                uri = contentUri,
                                name = name,
                                sizeBytes = size,
                                width = width,
                                height = height,
                                mimeType = mime
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    fun getExifDateFormatted(context: Context, uri: Uri): String {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (!dateStr.isNullOrBlank()) {
                    // EXIF format usually "yyyy:MM:dd HH:mm:ss"
                    val clean = dateStr.replace(":", "").replace(" ", "_")
                    if (clean.length >= 15) {
                        return clean.substring(0, 15)
                    }
                }
            }
        } catch (_: Exception) {}
        val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    fun getExifDetails(context: Context, uri: Uri): ExifDetails {
        var cameraModel: String? = null
        var dateTime: String? = null
        var iso: String? = null
        var aperture: String? = null
        var shutterSpeed: String? = null
        var focalLength: String? = null
        var flashUsed: Boolean? = null
        var lat: Double? = null
        var lon: Double? = null

        val meta = getImageMetadata(context, uri)
        val dimStr = "${meta.width} × ${meta.height} px"
        val sizeStr = formatBytes(meta.sizeBytes)

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                cameraModel = when {
                    !make.isNullOrBlank() && !model.isNullOrBlank() -> {
                        if (model.contains(make, ignoreCase = true)) model else "$make $model"
                    }
                    !model.isNullOrBlank() -> model
                    !make.isNullOrBlank() -> make
                    else -> null
                }

                dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

                val isoVal = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                if (!isoVal.isNullOrBlank()) iso = "ISO $isoVal"

                val fNum = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                if (!fNum.isNullOrBlank()) aperture = "f/$fNum"

                val expTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                if (!expTime.isNullOrBlank()) {
                    shutterSpeed = try {
                        val expVal = expTime.toDouble()
                        if (expVal < 1.0 && expVal > 0) {
                            "1/${(1.0 / expVal).roundToInt()}s"
                        } else {
                            "${expTime}s"
                        }
                    } catch (_: Exception) {
                        "${expTime}s"
                    }
                }

                val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                if (!focal.isNullOrBlank()) focalLength = "${focal}mm"

                val flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
                if (flash != -1) {
                    flashUsed = (flash and 1) != 0
                }

                val latLongArray = FloatArray(2)
                if (exif.getLatLong(latLongArray)) {
                    lat = latLongArray[0].toDouble()
                    lon = latLongArray[1].toDouble()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ExifDetails(
            cameraModel = cameraModel,
            dateTime = dateTime,
            dimensions = dimStr,
            fileSizeStr = sizeStr,
            iso = iso,
            aperture = aperture,
            shutterSpeed = shutterSpeed,
            focalLength = focalLength,
            flashUsed = flashUsed,
            latitude = lat,
            longitude = lon
        )
    }

    fun generateTargetFileName(
        pattern: com.example.model.RenamePattern,
        customPrefix: String,
        originalName: String,
        index: Int,
        format: ImageFormat,
        dateStr: String
    ): String {
        val seqStr = String.format(java.util.Locale.US, "%03d", index + 1)
        return when (pattern) {
            com.example.model.RenamePattern.KEEP_ORIGINAL -> {
                val clean = originalName.substringBeforeLast(".")
                "${clean}_edited_${System.currentTimeMillis()}.${format.extension}"
            }
            com.example.model.RenamePattern.DATE_TIME -> {
                "${dateStr}_${seqStr}.${format.extension}"
            }
            com.example.model.RenamePattern.CUSTOM_PREFIX -> {
                val prefix = if (customPrefix.isNotBlank()) customPrefix.trim() else "사진"
                "${prefix}_${seqStr}.${format.extension}"
            }
            com.example.model.RenamePattern.CLEAN_SIMPLE -> {
                "PhotoFit_${seqStr}.${format.extension}"
            }
        }
    }

    fun drawWatermark(
        source: Bitmap,
        text: String,
        position: com.example.model.WatermarkPosition
    ): Bitmap {
        if (text.isBlank()) return source
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val textSize = max(24f, source.height * 0.032f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 2f, 2f, Color.argb(180, 0, 0, 0))
        }

        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val textWidth = paint.measureText(text)
        val textHeight = textBounds.height()

        val margin = source.width * 0.04f
        val bgPaddingH = 16f
        val bgPaddingV = 10f

        val (x, y) = when (position) {
            com.example.model.WatermarkPosition.BOTTOM_RIGHT -> Pair(
                source.width - textWidth - margin,
                source.height - margin
            )
            com.example.model.WatermarkPosition.BOTTOM_LEFT -> Pair(
                margin,
                source.height - margin
            )
            com.example.model.WatermarkPosition.TOP_RIGHT -> Pair(
                source.width - textWidth - margin,
                margin + textHeight
            )
            com.example.model.WatermarkPosition.CENTER -> Pair(
                (source.width - textWidth) / 2f,
                (source.height + textHeight) / 2f
            )
        }

        // Pill background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 0, 0, 0)
        }
        val pillRect = android.graphics.RectF(
            x - bgPaddingH,
            y - textHeight - bgPaddingV,
            x + textWidth + bgPaddingH,
            y + bgPaddingV
        )
        canvas.drawRoundRect(pillRect, 16f, 16f, bgPaint)
        canvas.drawText(text, x, y, paint)

        return result
    }

    fun exportImagesToPdf(
        context: Context,
        uris: List<Uri>,
        pdfName: String = "PhotoFit_Document"
    ): Uri? {
        if (uris.isEmpty()) return null
        val pdfDocument = android.graphics.pdf.PdfDocument()

        try {
            val a4Width = 595
            val a4Height = 842

            for ((index, uri) in uris.withIndex()) {
                val bitmap = loadBitmap(context, uri, maxDimension = 1600) ?: continue

                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(a4Width, a4Height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Scale bitmap to fit within A4 margins
                val margin = 30f
                val maxWidth = a4Width - (margin * 2)
                val maxHeight = a4Height - (margin * 2)

                val scale = min(maxWidth / bitmap.width.toFloat(), maxHeight / bitmap.height.toFloat())
                val scaledW = bitmap.width * scale
                val scaledH = bitmap.height * scale

                val left = margin + (maxWidth - scaledW) / 2f
                val top = margin + (maxHeight - scaledH) / 2f

                val destRect = android.graphics.RectF(left, top, left + scaledW, top + scaledH)
                canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))

                pdfDocument.finishPage(page)
                bitmap.recycle()
            }

            val cacheFolder = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val cleanName = pdfName.substringBeforeLast(".")
            val pdfFile = File(cacheFolder, "${cleanName}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
                out.flush()
            }
            pdfDocument.close()

            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            return null
        }
    }
}
