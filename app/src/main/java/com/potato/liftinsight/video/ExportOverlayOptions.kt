package com.potato.liftinsight.video

data class ExportOverlayOptions(
    val showSkeleton: Boolean = false,
    val showAngleDisplay: Boolean = false,
    val showAnglePlot: Boolean = false,
    val showBarbellTrace: Boolean = false
) {
    val renderedItemsCode: String
        get() = buildString {
            if (showSkeleton) append('s')
            if (showAngleDisplay) append('d')
            if (showAnglePlot) append('p')
            if (showBarbellTrace) append('b')
        }

    val hasAnyOverlay: Boolean
        get() = showSkeleton || showAngleDisplay || showAnglePlot || showBarbellTrace
}
