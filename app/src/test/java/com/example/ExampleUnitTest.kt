package com.example

import com.example.model.BatchFormatChoice
import com.example.model.FilterPreset
import com.example.model.ImageAdjustment
import com.example.model.ImageFormat
import com.example.model.ResizeMode
import com.example.util.ImageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", ImageUtils.formatBytes(0))
        assertEquals("500 B", ImageUtils.formatBytes(500))
        assertEquals("1 KB", ImageUtils.formatBytes(1024))
        assertEquals("1.5 MB", ImageUtils.formatBytes((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun testResizeDimensions() {
        val (w75, h75) = ImageUtils.calculateTargetDimensions(2000, 1000, ResizeMode.PERCENT_75)
        assertEquals(1500, w75)
        assertEquals(750, h75)

        val (w50, h50) = ImageUtils.calculateTargetDimensions(2000, 1000, ResizeMode.PERCENT_50)
        assertEquals(1000, w50)
        assertEquals(500, h50)

        val (wFhd, hFhd) = ImageUtils.calculateTargetDimensions(3840, 2160, ResizeMode.FHD_1080P)
        assertEquals(1920, wFhd)
        assertEquals(1080, hFhd)
    }

    @Test
    fun testAdjustmentDefaults() {
        val adj = ImageAdjustment()
        assertTrue(adj.isDefault)
        assertEquals(FilterPreset.ORIGINAL, adj.filterPreset)
        assertEquals(0, adj.rotation)
    }

    @Test
    fun testImageFormats() {
        assertEquals("webp", ImageFormat.WEBP.extension)
        assertEquals("jpg", ImageFormat.JPEG.extension)
        assertEquals("png", ImageFormat.PNG.extension)
    }

    @Test
    fun testBatchConfigDefaults() {
        val config = com.example.model.BatchConfig()
        assertEquals(75, config.quality)
        assertEquals(com.example.model.BatchFormatChoice.KEEP_ORIGINAL, config.formatChoice)
        assertEquals(com.example.model.ResizeMode.ORIGINAL, config.resizeMode)
    }

    @Test
    fun testRenamePatternGenerator() {
        val fileName = ImageUtils.generateTargetFileName(
            com.example.model.RenamePattern.DATE_TIME,
            "",
            "long_messy_name.jpg",
            0,
            ImageFormat.JPEG,
            "20261112_143000"
        )
        assertEquals("20261112_143000_001.jpg", fileName)

        val prefixFileName = ImageUtils.generateTargetFileName(
            com.example.model.RenamePattern.CUSTOM_PREFIX,
            "제주여행",
            "test.png",
            2,
            ImageFormat.PNG,
            "20261112_143000"
        )
        assertEquals("제주여행_003.png", prefixFileName)

        val largeBatchName = ImageUtils.generateTargetFileName(
            com.example.model.RenamePattern.CLEAN_SIMPLE,
            "",
            "photo.jpg",
            19999,
            ImageFormat.WEBP,
            ""
        )
        assertEquals("PhotoFit_20000.webp", largeBatchName)
    }

    @Test
    fun testChunkItemsForLargeBatchDeletion() {
        val twentyThousand = (1..20000).toList()
        val chunks = ImageUtils.chunkItems(twentyThousand)
        assertEquals(40, chunks.size)
        assertEquals(ImageUtils.MAX_DELETE_BATCH_SIZE, chunks.first().size)
        assertEquals(ImageUtils.MAX_DELETE_BATCH_SIZE, chunks.last().size)
        assertEquals(20000, chunks.sumOf { it.size })

        assertTrue(ImageUtils.chunkItems(emptyList<Int>()).isEmpty())
    }

    @Test
    fun testKeepOriginalFileNameHasNoTimestampSuffix() {
        val kept = ImageUtils.generateTargetFileName(
            com.example.model.RenamePattern.KEEP_ORIGINAL,
            "",
            "IMG_20220501_142030.jpg",
            0,
            ImageFormat.WEBP,
            ""
        )
        assertEquals("IMG_20220501_142030.webp", kept)
    }

    @Test
    fun testEffectiveDateResolutionAndExifFormatting() {
        assertEquals(1700000000123L, ImageUtils.resolveEffectiveDateMs(1700000000123L, 1600000000L, 1650000000L))
        assertEquals(1600000000000L, ImageUtils.resolveEffectiveDateMs(0L, 1600000000L, 1650000000L))
        assertEquals(1650000000000L, ImageUtils.resolveEffectiveDateMs(0L, 0L, 1650000000L))
        assertEquals(0L, ImageUtils.resolveEffectiveDateMs(0L, 0L, 0L))

        val parsed = ImageUtils.parseExifDateTimeToMillis("2022:08:15 09:30:45")
        assertTrue(parsed > 0L)
        assertEquals("2022:08:15 09:30:45", ImageUtils.formatExifDateTime(parsed))
        assertEquals(0L, ImageUtils.parseExifDateTimeToMillis("invalid"))
    }

    @Test
    fun testExtractDateFromFileName() {
        val cam = ImageUtils.extractDateFromFileName("IMG_20220501_142030.jpg")
        assertNotNull(cam)
        assertEquals("2022-05-01 14:20:30", ImageUtils.formatReadableDateTime(cam!!))

        val edited = ImageUtils.extractDateFromFileName("IMG_20220501_142030_edited_1794600000000.webp")
        assertNotNull(edited)
        assertEquals("2022-05-01 14:20:30", ImageUtils.formatReadableDateTime(edited!!))

        val kakao = ImageUtils.extractDateFromFileName("KakaoTalk_20240315_183012345.jpg")
        assertNotNull(kakao)
        assertEquals("2024-03-15 18:30:12", ImageUtils.formatReadableDateTime(kakao!!))

        val screenshot = ImageUtils.extractDateFromFileName("Screenshot_2023-07-10-15-20-30-123_com.app.png")
        assertNotNull(screenshot)
        assertEquals("2023-07-10 15:20:30", ImageUtils.formatReadableDateTime(screenshot!!))

        val dateOnly = ImageUtils.extractDateFromFileName("20220501_001.jpg")
        assertNotNull(dateOnly)
        assertEquals("2022-05-01 12:00:00", ImageUtils.formatReadableDateTime(dateOnly!!))

        assertEquals(null, ImageUtils.extractDateFromFileName("PhotoFit_001.webp"))
        assertEquals(null, ImageUtils.extractDateFromFileName("IMG_20221345_999999.jpg"))
    }
}
