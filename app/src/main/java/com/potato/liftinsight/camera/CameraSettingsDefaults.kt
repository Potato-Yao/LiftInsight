package com.potato.liftinsight.camera

import androidx.camera.video.Quality

data class QualityOption(
    val quality: Quality,
    val label: String
)

data class CameraSettings(
    val quality: Quality,
    val targetFrameRate: Int
)

object CameraSettingsDefaults {

    private val QUALITY_LABELS = mapOf(
        Quality.UHD to "2160p (4K)",
        Quality.FHD to "1080p (FHD)",
        Quality.HD to "720p (HD)",
        Quality.SD to "480p (SD)"
    )

    fun labelForQuality(quality: Quality): String {
        return QUALITY_LABELS[quality] ?: quality.toString()
    }

    fun buildQualityOptions(supportedQualities: Set<Quality>): List<QualityOption> {
        return listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
            .filter { it in supportedQualities }
            .map { QualityOption(it, labelForQuality(it)) }
    }

    fun pickDefaultQuality(supportedQualities: Set<Quality>): Quality {
        return when {
            Quality.FHD in supportedQualities -> Quality.FHD
            Quality.HD in supportedQualities -> Quality.HD
            Quality.SD in supportedQualities -> Quality.SD
            Quality.UHD in supportedQualities -> Quality.UHD
            else -> Quality.HD
        }
    }

    fun pickDefaultFrameRate(supportedFrameRates: List<Int>): Int {
        return supportedFrameRates.maxOrNull() ?: 30
    }

    fun buildFrameRateOptions(supportedFpsRanges: List<Pair<Int, Int>>): List<Int> {
        val maxRates = supportedFpsRanges
            .map { (_, high) -> high }
            .filter { it in listOf(24, 30, 60, 90, 120, 240) }
            .distinct()
            .sortedDescending()

        return if (maxRates.isNotEmpty()) maxRates else listOf(30, 60)
    }

    fun defaultSettings(
        supportedQualities: Set<Quality>,
        supportedFrameRates: List<Int>
    ): CameraSettings {
        return CameraSettings(
            quality = pickDefaultQuality(supportedQualities),
            targetFrameRate = pickDefaultFrameRate(supportedFrameRates)
        )
    }
}
