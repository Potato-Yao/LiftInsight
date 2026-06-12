package com.potato.liftinsight.video

import android.graphics.Bitmap
import com.google.mlkit.vision.pose.PoseLandmark
import com.potato.liftinsight.common.logging.AppLogger
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

internal data class DetectedCircle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val confidence: Float
)

internal data class CircleDetectionResult(
    val circles: List<DetectedCircle>,
    val grayMat: Mat
)

internal data class WeightPlateCandidate(
    val x: Float,
    val y: Float,
    val radius: Float,
    val confidence: Float,
    val nearHand: Boolean
)

internal class BarbellDetectionService(
    private val logger: AppLogger
) {
    @Volatile
    private var isInitialized = false

    fun initialize(): Boolean {
        if (isInitialized) return true
        return synchronized(this) {
            if (isInitialized) return@synchronized true
            try {
                isInitialized = OpenCVLoader.initLocal()
                if (isInitialized) {
                    logger.info(TAG, "OpenCV initialized successfully: version=${OpenCVLoader.OPENCV_VERSION}")
                } else {
                    logger.warn(TAG, "OpenCV initialization failed")
                }
                isInitialized
            } catch (e: Exception) {
                logger.error(TAG, "OpenCV initialization exception", e)
                false
            }
        }
    }

    fun detectCircles(bitmap: Bitmap): List<DetectedCircle> {
        if (!isInitialized) {
            if (!initialize()) {
                return emptyList()
            }
        }

        val rgbaMat = Mat()
        val grayMat = Mat()
        val blurredMat = Mat()
        val circles = Mat()

        try {
            Utils.bitmapToMat(bitmap, rgbaMat)
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(9.0, 9.0), 2.0)

            Imgproc.HoughCircles(
                blurredMat,
                circles,
                Imgproc.HOUGH_GRADIENT,
                DP,
                MIN_DIST,
                PARAM1,
                PARAM2,
                MIN_RADIUS,
                MAX_RADIUS
            )

            val result = mutableListOf<DetectedCircle>()
            if (circles.cols() > 0) {
                for (i in 0 until circles.cols()) {
                    val data = circles[0, i] ?: continue
                    val x = data[0].toFloat()
                    val y = data[1].toFloat()
                    val radius = data[2].toFloat()

                    // Filter: circle should be within frame bounds
                    if (x < 0 || y < 0) continue
                    if (radius < MIN_RADIUS_FILTER || radius > MAX_RADIUS_FILTER) continue

                    // Validate circularity of the candidate region
                    val circularity = validateCircularity(grayMat, x.toDouble(), y.toDouble(), radius.toDouble())
                    if (circularity < MIN_CIRCULARITY) continue

                    result += DetectedCircle(
                        x = x,
                        y = y,
                        radius = radius,
                        confidence = 1.0f
                    )
                }
            }

            logger.trace(TAG, "Detected ${result.size} circles in frame")
            return result
        } catch (e: Exception) {
            logger.error(TAG, "Circle detection failed", e)
            return emptyList()
        } finally {
            rgbaMat.release()
            grayMat.release()
            blurredMat.release()
            circles.release()
        }
    }

    fun detectCirclesNormalized(bitmap: Bitmap): List<DetectedCircle> {
        val rawCircles = detectCircles(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return rawCircles.map { circle ->
            circle.copy(
                x = circle.x / w,
                y = circle.y / h,
                radius = circle.radius / w.coerceAtMost(h)
            )
        }
    }

    /**
     * Detects weight plate candidates in a frame, filtered by proximity to hand landmarks.
     *
     * When hand landmarks are available with high confidence (>0.5), only circles within
     * [HAND_PROXIMITY_THRESHOLD] pixels from the hand center are returned. When no hand
     * landmarks are available or visible, all detected circles are returned without filtering.
     */
    fun detectWeightPlateCandidates(
        bitmap: Bitmap,
        handLandmarks: Map<Int, PoseOverlayLandmark>?
    ): List<WeightPlateCandidate> {
        val circles = detectCircles(bitmap)
        if (circles.isEmpty()) return emptyList()

        val handCenter = handLandmarks?.let { computeHandCenter(it) }

        return circles.map { circle ->
            val nearHand = if (handCenter != null) {
                val dist = distance(circle.x, circle.y, handCenter.first, handCenter.second)
                dist <= HAND_PROXIMITY_THRESHOLD
            } else {
                false
            }
            WeightPlateCandidate(
                x = circle.x,
                y = circle.y,
                radius = circle.radius,
                confidence = circle.confidence,
                nearHand = nearHand
            )
        }.let { candidates ->
            if (handCenter != null) {
                // When hand landmarks are available, only return circles near the hand
                candidates.filter { it.nearHand }
            } else {
                // No hand landmarks — return all candidates
                candidates
            }
        }
    }

    private fun computeHandCenter(landmarks: Map<Int, PoseOverlayLandmark>): Pair<Float, Float>? {
        val handLandmarkTypes = listOf(
            PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_INDEX,
            PoseLandmark.RIGHT_INDEX,
            PoseLandmark.LEFT_THUMB,
            PoseLandmark.RIGHT_THUMB
        )

        var sumX = 0f
        var sumY = 0f
        var count = 0

        for (type in handLandmarkTypes) {
            val landmark = landmarks[type] ?: continue
            if (landmark.visibility < 0.5f) continue
            sumX += landmark.x
            sumY += landmark.y
            count++
        }

        if (count == 0) return null
        return Pair(sumX / count, sumY / count)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /**
     * Validates the circularity of a detected circle candidate by examining the actual
     * image content within the candidate region. Creates a circular mask, applies it
     * to the grayscale image, runs Canny edge detection, and measures the circularity
     * of the largest contour found.
     *
     * @return circularity value in [0.0, 1.0], where 1.0 is a perfect circle
     */
    private fun validateCircularity(grayMat: Mat, cx: Double, cy: Double, radius: Double): Double {
        val mask = Mat.zeros(grayMat.rows(), grayMat.cols(), CvType.CV_8UC1)
        val maskedRegion = Mat()
        val edges = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()

        try {
            // Draw filled circle on mask
            Imgproc.circle(mask, Point(cx, cy), radius.toInt(), Scalar(255.0), -1)
            // Extract the circular region from the grayscale image
            Core.bitwise_and(grayMat, grayMat, maskedRegion, mask)
            // Detect edges in the extracted region
            Imgproc.Canny(maskedRegion, edges, 50.0, 150.0)
            // Find contours of the detected edges
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            var bestCircularity = 0.0
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area <= 0.0) continue
                val mp2f = MatOfPoint2f()
                contour.convertTo(mp2f, CvType.CV_32FC2)
                val perimeter = Imgproc.arcLength(mp2f, true)
                mp2f.release()
                if (perimeter <= 0.0) continue
                val circularity = 4.0 * Math.PI * area / (perimeter * perimeter)
                if (circularity > bestCircularity) {
                    bestCircularity = circularity
                }
            }
            return bestCircularity.coerceIn(0.0, 1.0)
        } finally {
            mask.release()
            maskedRegion.release()
            edges.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    companion object {
        private const val TAG = "BarbellDetectionService"

        // HoughCircles parameters
        private const val DP = 1.2
        private const val MIN_DIST = 50.0
        private const val PARAM1 = 100.0
        private const val PARAM2 = 55.0
        private const val MIN_RADIUS = 15
        private const val MAX_RADIUS = 150

        // Post-detection filters
        private const val MIN_RADIUS_FILTER = 12f
        private const val MAX_RADIUS_FILTER = 160f

        // Circularity threshold (0.0–1.0, higher = more circular)
        private const val MIN_CIRCULARITY = 0.7

        // Hand proximity threshold in pixels for weight plate candidate filtering
        private const val HAND_PROXIMITY_THRESHOLD = 200f
    }
}
