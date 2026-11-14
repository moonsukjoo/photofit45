package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class ImageFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val description: String,
    val supportsQuality: Boolean
) {
    JPEG("jpg", "image/jpeg", "JPEG", "대중적인 호환성, 사진에 최적", true),
    WEBP("webp", "image/webp", "WEBP", "초고효율 압축 & 고화질 (추천)", true),
    PNG("png", "image/png", "PNG", "무손실 압축, 투명도 지원", false);

    fun toCompressFormat(): Bitmap.CompressFormat {
        return when (this) {
            JPEG -> Bitmap.CompressFormat.JPEG
            PNG -> Bitmap.CompressFormat.PNG
            WEBP -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }
    }
}

enum class BatchFormatChoice(val displayName: String, val format: ImageFormat?) {
    KEEP_ORIGINAL("원본 포맷 유지", null),
    WEBP("WEBP (초고효율 압축)", ImageFormat.WEBP),
    JPEG("JPEG (호환성 최고)", ImageFormat.JPEG),
    PNG("PNG (무손실 투명도)", ImageFormat.PNG)
}

enum class FilterPreset(val displayName: String, val subtitle: String) {
    ORIGINAL("원본", "효과 없음"),
    VIVID("생생하게", "채도 및 선명도 강화"),
    WARM("따뜻한 감성", "골든 아워 느낌의 따스한 톤"),
    COOL("시원한 쿨톤", "차분하고 맑은 푸른빛"),
    MONO("클래식 흑백", "깊이감 있는 모노크롬"),
    SEPIA("빈티지 세피아", "필름 카메라 아날로그 감성"),
    DRAMATIC("드라마틱", "강렬한 명암 대비"),
    SOFT("소프트 글로우", "부드럽고 몽환적인 톤"),
    CYBER("사이버 네온", "감각적인 사이버펑크 틴트")
}

enum class CropRatio(val displayName: String, val ratio: Float?) {
    FREE("자유", null),
    RATIO_1_1("1:1 정방형", 1.0f),
    RATIO_4_3("4:3 기본", 4f / 3f),
    RATIO_3_4("3:4 인물", 3f / 4f),
    RATIO_16_9("16:9 와이드", 16f / 9f),
    RATIO_9_16("9:16 세로형", 9f / 16f)
}

enum class ResizeMode(val displayName: String, val scale: Float?, val maxDimension: Int?) {
    ORIGINAL("원본 크기 유지", 1.0f, null),
    PERCENT_75("75% 축소", 0.75f, null),
    PERCENT_50("50% 축소 (용량 대폭 절감)", 0.50f, null),
    PERCENT_25("25% 축소 (초소형 썸네일)", 0.25f, null),
    FHD_1080P("FHD 고화질 (1080px)", null, 1920),
    HD_720P("HD 일반 (720px)", null, 1280),
    INSTA_FEED("인스타 피드 (1080px)", null, 1080),
    KAKAO_PROFILE("카카오 프로필 (640px)", null, 640),
    WEB_THUMB("웹 썸네일 (500px)", null, 500),
    CUSTOM("직접 크기 지정", null, null)
}

data class ExifDetails(
    val cameraModel: String? = null,
    val dateTime: String? = null,
    val dimensions: String? = null,
    val fileSizeStr: String? = null,
    val iso: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val focalLength: String? = null,
    val flashUsed: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val hasGps: Boolean
        get() = latitude != null && longitude != null
}

data class ImageAdjustment(
    val brightness: Float = 0f,    // -100f .. +100f
    val contrast: Float = 0f,      // -100f .. +100f
    val saturation: Float = 0f,    // -100f .. +100f
    val warmth: Float = 0f,        // -100f .. +100f
    val vignette: Float = 0f,      // 0f .. 100f
    val filterPreset: FilterPreset = FilterPreset.ORIGINAL,
    val rotation: Int = 0,         // 0, 90, 180, 270
    val flipHorizontal: Boolean = false
) {
    val isDefault: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f &&
                warmth == 0f && vignette == 0f && filterPreset == FilterPreset.ORIGINAL &&
                rotation == 0 && !flipHorizontal
}

data class SingleEditConfig(
    val quality: Int = 75,
    val format: ImageFormat = ImageFormat.JPEG,
    val resizeMode: ResizeMode = ResizeMode.ORIGINAL,
    val customWidth: Int = 0,
    val customHeight: Int = 0,
    val lockAspectRatio: Boolean = true,
    val targetMaxKb: Int? = null,
    val deleteOriginalOnSave: Boolean = false,
    val adjustments: ImageAdjustment = ImageAdjustment(),
    val watermarkText: String = "",
    val watermarkPosition: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val removeExifMetadata: Boolean = true,
    val preserveOriginalDate: Boolean = true
)

enum class BatchItemStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class BatchProcessItem(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val originalSizeBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val mimeType: String,
    val originalDateTakenMs: Long = 0L,
    val processedSizeBytes: Long? = null,
    val resultUri: Uri? = null,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val errorMessage: String? = null,
    val isSelected: Boolean = true
) {
    val savedBytes: Long
        get() = if (processedSizeBytes != null && processedSizeBytes < originalSizeBytes) {
            originalSizeBytes - processedSizeBytes
        } else 0L

    val savingsPercent: Int
        get() = if (processedSizeBytes != null && originalSizeBytes > 0) {
            val ratio = (1.0 - (processedSizeBytes.toDouble() / originalSizeBytes.toDouble())) * 100
            ratio.coerceAtLeast(0.0).toInt()
        } else 0
}

enum class RenamePattern(val displayName: String, val example: String) {
    KEEP_ORIGINAL("원본 파일명 유지", "기존 파일명 그대로"),
    DATE_TIME("촬영 날짜_시간_순번", "20261112_143025_001.jpg"),
    CUSTOM_PREFIX("사용자 지정 접두사_순번", "제주여행_001.jpg"),
    CLEAN_SIMPLE("포토핏_순번", "PhotoFit_001.jpg")
}

enum class WatermarkPosition(val displayName: String) {
    BOTTOM_RIGHT("우측 하단"),
    BOTTOM_LEFT("좌측 하단"),
    TOP_RIGHT("우측 상단"),
    CENTER("중앙")
}

data class BatchConfig(
    val quality: Int = 75,
    val formatChoice: BatchFormatChoice = BatchFormatChoice.KEEP_ORIGINAL,
    val resizeMode: ResizeMode = ResizeMode.ORIGINAL,
    val deleteOriginal: Boolean = false,
    val targetMaxKb: Int? = null,
    val renamePattern: RenamePattern = RenamePattern.KEEP_ORIGINAL,
    val customPrefix: String = "",
    val removeExifMetadata: Boolean = true,
    val preserveOriginalDate: Boolean = true,
    val watermarkText: String = "",
    val watermarkPosition: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT
)

data class SavedHistoryItem(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val originalSizeBytes: Long,
    val savedSizeBytes: Long,
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val savedAtTimestamp: Long = System.currentTimeMillis()
)
