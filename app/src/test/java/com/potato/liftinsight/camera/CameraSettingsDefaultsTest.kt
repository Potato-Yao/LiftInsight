package com.potato.liftinsight.camera

import androidx.camera.video.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class CameraSettingsDefaultsTest {

    @Test
    fun pickDefaultQuality_prefersFhdOverOthers() {
        val supported = setOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
        assertEquals(Quality.FHD, CameraSettingsDefaults.pickDefaultQuality(supported))
    }

    @Test
    fun pickDefaultQuality_fallsBackToHdWhenFhdNotSupported() {
        val supported = setOf(Quality.HD, Quality.SD)
        assertEquals(Quality.HD, CameraSettingsDefaults.pickDefaultQuality(supported))
    }

    @Test
    fun pickDefaultQuality_fallsBackToSdWhenOnlySdSupported() {
        val supported = setOf(Quality.SD)
        assertEquals(Quality.SD, CameraSettingsDefaults.pickDefaultQuality(supported))
    }

    @Test
    fun pickDefaultQuality_fallsBackToUhdWhenOnlyUhdSupported() {
        val supported = setOf(Quality.UHD)
        assertEquals(Quality.UHD, CameraSettingsDefaults.pickDefaultQuality(supported))
    }

    @Test
    fun pickDefaultQuality_usesHdWhenNothingSupported() {
        val supported = emptySet<Quality>()
        assertEquals(Quality.HD, CameraSettingsDefaults.pickDefaultQuality(supported))
    }

    @Test
    fun pickDefaultFrameRate_returnsMaxOfList() {
        val rates = listOf(24, 30, 60, 120)
        assertEquals(120, CameraSettingsDefaults.pickDefaultFrameRate(rates))
    }

    @Test
    fun pickDefaultFrameRate_returnsDefaultWhenEmpty() {
        val rates = emptyList<Int>()
        assertEquals(30, CameraSettingsDefaults.pickDefaultFrameRate(rates))
    }

    @Test
    fun pickDefaultFrameRate_returnsSingleElement() {
        val rates = listOf(15)
        assertEquals(15, CameraSettingsDefaults.pickDefaultFrameRate(rates))
    }

    @Test
    fun labelForQuality_returnsLabelForFhd() {
        assertEquals("1080p (FHD)", CameraSettingsDefaults.labelForQuality(Quality.FHD))
    }

    @Test
    fun labelForQuality_returnsLabelForHd() {
        assertEquals("720p (HD)", CameraSettingsDefaults.labelForQuality(Quality.HD))
    }

    @Test
    fun labelForQuality_returnsLabelForSd() {
        assertEquals("480p (SD)", CameraSettingsDefaults.labelForQuality(Quality.SD))
    }

    @Test
    fun labelForQuality_returnsLabelForUhd() {
        assertEquals("2160p (4K)", CameraSettingsDefaults.labelForQuality(Quality.UHD))
    }

    @Test
    fun buildQualityOptions_returnsOnlySupportedQualitiesInOrder() {
        val supported = setOf(Quality.FHD, Quality.HD)
        val options = CameraSettingsDefaults.buildQualityOptions(supported)
        assertEquals(2, options.size)
        assertEquals(Quality.FHD, options[0].quality)
        assertEquals(Quality.HD, options[1].quality)
    }

    @Test
    fun buildQualityOptions_returnsEmptyForNoSupported() {
        val supported = emptySet<Quality>()
        val options = CameraSettingsDefaults.buildQualityOptions(supported)
        assertTrue(options.isEmpty())
    }

    @Test
    fun buildFrameRateOptions_filtersCommonRates() {
        val fpsRanges = listOf(
            Pair(15, 30),
            Pair(15, 60),
            Pair(30, 60),
            Pair(30, 120)
        )
        val options = CameraSettingsDefaults.buildFrameRateOptions(fpsRanges)
        assertTrue(120 in options)
        assertTrue(60 in options)
        assertTrue(30 in options)
        assertEquals(listOf(120, 60, 30), options)
    }

    @Test
    fun buildFrameRateOptions_fallsBackToDefaultsWhenEmpty() {
        val options = CameraSettingsDefaults.buildFrameRateOptions(emptyList())
        assertEquals(listOf(30, 60), options)
    }

    @Test
    fun defaultSettings_usesFhdAndMaxFrameRate() {
        val supportedQualities = setOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
        val frameRates = listOf(30, 60)
        val settings = CameraSettingsDefaults.defaultSettings(supportedQualities, frameRates)
        assertEquals(Quality.FHD, settings.quality)
        assertEquals(60, settings.targetFrameRate)
    }

    @Test
    fun defaultSettings_fallsBackWhenFhdUnavailable() {
        val supportedQualities = setOf(Quality.HD, Quality.SD)
        val frameRates = listOf(24, 30)
        val settings = CameraSettingsDefaults.defaultSettings(supportedQualities, frameRates)
        assertEquals(Quality.HD, settings.quality)
        assertEquals(30, settings.targetFrameRate)
    }

    @Test
    fun cameraSettings_equalsAndHashCode() {
        val a = CameraSettings(Quality.FHD, 60)
        val b = CameraSettings(Quality.FHD, 60)
        val c = CameraSettings(Quality.HD, 30)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    @Test
    fun qualityOption_equalsAndHashCode() {
        val a = QualityOption(Quality.FHD, "1080p (FHD)")
        val b = QualityOption(Quality.FHD, "1080p (FHD)")
        val c = QualityOption(Quality.HD, "720p (HD)")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }
}
