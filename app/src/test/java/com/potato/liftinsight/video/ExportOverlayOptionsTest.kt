package com.potato.liftinsight.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportOverlayOptionsTest {

    @Test
    fun `renderedItemsCode returns empty when no overlay selected`() {
        val options = ExportOverlayOptions()
        assertEquals("", options.renderedItemsCode)
    }

    @Test
    fun `renderedItemsCode returns s when only skeleton selected`() {
        val options = ExportOverlayOptions(showSkeleton = true)
        assertEquals("s", options.renderedItemsCode)
    }

    @Test
    fun `renderedItemsCode returns d when only angle display selected`() {
        val options = ExportOverlayOptions(showAngleDisplay = true)
        assertEquals("d", options.renderedItemsCode)
    }

    @Test
    fun `renderedItemsCode returns p when only angle plot selected`() {
        val options = ExportOverlayOptions(showAnglePlot = true)
        assertEquals("p", options.renderedItemsCode)
    }

    @Test
    fun `renderedItemsCode returns b when only barbell trace selected`() {
        val options = ExportOverlayOptions(showBarbellTrace = true)
        assertEquals("b", options.renderedItemsCode)
    }

    @Test
    fun `renderedItemsCode returns correct code for all overlays`() {
        val options = ExportOverlayOptions(
            showSkeleton = true,
            showAngleDisplay = true,
            showAnglePlot = true,
            showBarbellTrace = true
        )
        assertEquals("sdpb", options.renderedItemsCode)
    }

    @Test
    fun `renderedItemsCode returns sdp for skeleton angle_display angle_plot`() {
        val options = ExportOverlayOptions(
            showSkeleton = true,
            showAngleDisplay = true,
            showAnglePlot = true
        )
        assertEquals("sdp", options.renderedItemsCode)
    }

    @Test
    fun `renderedItemsCode returns sd for skeleton and angle_display`() {
        val options = ExportOverlayOptions(
            showSkeleton = true,
            showAngleDisplay = true
        )
        assertEquals("sd", options.renderedItemsCode)
    }

    @Test
    fun `renderedItemsCode returns sp for skeleton and angle_plot`() {
        val options = ExportOverlayOptions(
            showSkeleton = true,
            showAnglePlot = true
        )
        assertEquals("sp", options.renderedItemsCode)
    }

    @Test
    fun `renderedItemsCode returns dp for angle_display and angle_plot`() {
        val options = ExportOverlayOptions(
            showAngleDisplay = true,
            showAnglePlot = true
        )
        assertEquals("dp", options.renderedItemsCode)
    }

    @Test
    fun `renderedItemsCode respects dialog order s d p b regardless of property order`() {
        val options = ExportOverlayOptions(
            showAnglePlot = true,
            showSkeleton = true
        )
        assertEquals("sp", options.renderedItemsCode)
    }

    @Test
    fun `hasAnyOverlay returns false by default`() {
        val options = ExportOverlayOptions()
        assertFalse(options.hasAnyOverlay)
    }

    @Test
    fun `hasAnyOverlay returns true when skeleton selected`() {
        val options = ExportOverlayOptions(showSkeleton = true)
        assertTrue(options.hasAnyOverlay)
    }

    @Test
    fun `hasAnyOverlay returns true when angle display selected`() {
        val options = ExportOverlayOptions(showAngleDisplay = true)
        assertTrue(options.hasAnyOverlay)
    }

    @Test
    fun `hasAnyOverlay returns true when angle plot selected`() {
        val options = ExportOverlayOptions(showAnglePlot = true)
        assertTrue(options.hasAnyOverlay)
    }

    @Test
    fun `hasAnyOverlay returns true when barbell trace selected`() {
        val options = ExportOverlayOptions(showBarbellTrace = true)
        assertTrue(options.hasAnyOverlay)
    }

    @Test
    fun `hasAnyOverlay returns true when multiple overlays selected`() {
        val options = ExportOverlayOptions(
            showSkeleton = true,
            showAngleDisplay = true
        )
        assertTrue(options.hasAnyOverlay)
    }
}
