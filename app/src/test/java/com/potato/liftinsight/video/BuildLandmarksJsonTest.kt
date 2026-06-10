package com.potato.liftinsight.video

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildLandmarksJsonTest {

    @Test
    fun emptyPositionsProducesEmptyJsonArray() {
        val result = buildLandmarksJsonInternal(emptyMap(), 640, 480)

        assertEquals("[]", result)
    }

    @Test
    fun singleLandmarkProducesCorrectJson() {
        val positions = mapOf(
            0 to PoseOverlayLandmark(x = 320f, y = 240f, visibility = 0.95f)
        )

        val result = buildLandmarksJsonInternal(positions, frameWidth = 640, frameHeight = 480)

        val jsonArray = JSONArray(result)
        assertEquals(1, jsonArray.length())

        val obj = jsonArray.getJSONObject(0)
        assertEquals(0, obj.getInt("t"))
        assertEquals(0.5, obj.getDouble("x"), 0.001)
        assertEquals(0.5, obj.getDouble("y"), 0.001)
        assertEquals(0.95, obj.getDouble("v"), 0.001)
    }

    @Test
    fun multipleLandmarksAreAllIncluded() {
        val positions = mapOf(
            0 to PoseOverlayLandmark(x = 100f, y = 200f, visibility = 0.9f),
            11 to PoseOverlayLandmark(x = 300f, y = 400f, visibility = 0.85f),
            12 to PoseOverlayLandmark(x = 500f, y = 100f, visibility = 0.8f)
        )

        val result = buildLandmarksJsonInternal(positions, frameWidth = 640, frameHeight = 480)

        val jsonArray = JSONArray(result)
        assertEquals(3, jsonArray.length())

        val types = mutableSetOf<Int>()
        for (i in 0 until jsonArray.length()) {
            types.add(jsonArray.getJSONObject(i).getInt("t"))
        }
        assertEquals(setOf(0, 11, 12), types)
    }

    @Test
    fun coordinatesAreProperlyNormalized() {
        val positions = mapOf(
            0 to PoseOverlayLandmark(x = 0f, y = 0f, visibility = 1.0f),
            1 to PoseOverlayLandmark(x = 640f, y = 480f, visibility = 0.5f)
        )

        val result = buildLandmarksJsonInternal(positions, frameWidth = 640, frameHeight = 480)

        val jsonArray = JSONArray(result)
        assertEquals(2, jsonArray.length())

        // First landmark should be at (0,0)
        val firstObj = jsonArray.getJSONObject(0)
        assertEquals(0.0, firstObj.getDouble("x"), 0.001)
        assertEquals(0.0, firstObj.getDouble("y"), 0.001)
        assertEquals(1.0, firstObj.getDouble("v"), 0.001)

        // Second landmark should be normalized to (1,1)
        val secondObj = jsonArray.getJSONObject(1)
        assertEquals(1.0, secondObj.getDouble("x"), 0.001)
        assertEquals(1.0, secondObj.getDouble("y"), 0.001)
        assertEquals(0.5, secondObj.getDouble("v"), 0.001)
    }

    @Test
    fun landmarksWithPartialVisibilityProduceCorrectVisibilityValues() {
        val positions = mapOf(
            0 to PoseOverlayLandmark(x = 160f, y = 120f, visibility = 0.0f),
            1 to PoseOverlayLandmark(x = 320f, y = 240f, visibility = 0.25f),
            2 to PoseOverlayLandmark(x = 480f, y = 360f, visibility = 0.75f)
        )

        val result = buildLandmarksJsonInternal(positions, frameWidth = 640, frameHeight = 480)

        val jsonArray = JSONArray(result)
        assertEquals(3, jsonArray.length())

        val visibilityValues = (0 until jsonArray.length()).map { i ->
            jsonArray.getJSONObject(i).getDouble("v")
        }.toSet()
        assertTrue(visibilityValues.contains(0.0))
        assertTrue(visibilityValues.contains(0.25))
        assertTrue(visibilityValues.contains(0.75))
    }
}
