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
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class DetectedCircle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val confidence: Float
)

internal data class DetectedLine(
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    val centerX: Float, val centerY: Float,
    val angle: Float,
    val length: Float,
    val confidence: Float
)

internal sealed class BarbellDetectionResult {
    data class Line(val line: DetectedLine) : BarbellDetectionResult()
    data class Circle(val circle: DetectedCircle) : BarbellDetectionResult()
}

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

    /**
     * Line-based barbell detection using HoughLinesP near wrist landmarks.
     *
     * Ported from Python prototype detect_barbell_near_wrists in tools/main.py.
     * Searches for near-horizontal line segments in the region around the lifter's wrists.
     * This is the primary detection method for front/side views where the barbell shaft is visible.
     *
     * @param bitmap The frame bitmap to analyze
     * @param handLandmarks Pose landmarks in pixel coordinates (must include wrists)
     * @return DetectedLine or null if no suitable line found
     */
    fun detectBarbellLine(
        bitmap: Bitmap,
        handLandmarks: Map<Int, PoseOverlayLandmark>?
    ): DetectedLine? {
        if (!isInitialized) {
            if (!initialize()) return null
        }

        if (handLandmarks == null) return null

        // Get wrist positions
        val leftWrist = handLandmarks[PoseLandmark.LEFT_WRIST]
        val rightWrist = handLandmarks[PoseLandmark.RIGHT_WRIST]

        val visibleWrists = listOfNotNull(leftWrist, rightWrist).filter { it.visibility >= 0.5f }
        if (visibleWrists.isEmpty()) return null

        // Compute wrist anchor (midpoint of visible wrists, or single wrist position)
        val wristAnchorX = visibleWrists.map { it.x }.average().toFloat()
        val wristAnchorY = visibleWrists.map { it.y }.average().toFloat()

        // Compute reference distance for ROI sizing
        val refDist = if (visibleWrists.size >= 2) {
            val wx = visibleWrists[0].x - visibleWrists[1].x
            val wy = visibleWrists[0].y - visibleWrists[1].y
            sqrt((wx * wx + wy * wy).toDouble()).toFloat()
        } else {
            150f
        }

        val frameW = bitmap.width
        val frameH = bitmap.height

        val rdist = refDist.coerceAtLeast(100f)
        val padX = (rdist * LINE_ROI_PAD_X_RATIO).coerceAtLeast(150f)
        val padY = (rdist * LINE_ROI_PAD_Y_RATIO).coerceAtLeast(100f)

        // Define ROI clamped to frame bounds
        val roiLeft = max(0f, wristAnchorX - padX).toInt()
        val roiTop = max(0f, wristAnchorY - padY).toInt()
        val roiRight = min(frameW.toFloat(), wristAnchorX + padX).toInt()
        val roiBottom = min(frameH.toFloat(), wristAnchorY + padY).toInt()

        if (roiRight - roiLeft < 20 || roiBottom - roiTop < 20) return null

        val rgbaMat = Mat()
        val grayMat = Mat()
        var grayROI: Mat? = null
        val edges = Mat()
        val dilated = Mat()
        val lines = Mat()
        var kernel: Mat? = null

        try {
            Utils.bitmapToMat(bitmap, rgbaMat)
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            val roi = Rect(roiLeft, roiTop, roiRight - roiLeft, roiBottom - roiTop)
            grayROI = Mat(grayMat, roi)

            // Edge detection in ROI
            val roiMat = grayROI ?: return null
            Imgproc.Canny(roiMat, edges, LINE_CANNY_LOW, LINE_CANNY_HIGH)

            // Dilate to connect nearby edge segments
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edges, dilated, kernel)

            val minLineLength = max(50f, refDist * LINE_MIN_LENGTH_RATIO)

            // HoughLinesP to detect line segments
            Imgproc.HoughLinesP(
                dilated, lines,
                1.0, Math.PI / 180.0,
                LINE_HOUGH_THRESHOLD,
                minLineLength.toDouble(),
                LINE_MAX_LINE_GAP.toDouble()
            )

            val minLineLenFilter = minLineLength

            var bestLine: DetectedLine? = null
            var bestScore = Double.NEGATIVE_INFINITY

            val cols = lines.cols()
            for (i in 0 until cols) {
                val data = lines[0, i] ?: continue
                val x1 = data[0].toFloat()
                val y1 = data[1].toFloat()
                val x2 = data[2].toFloat()
                val y2 = data[3].toFloat()

                val dx = x2 - x1
                val dy = y2 - y1
                val lineAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                val tiltDeg = abs(lineAngle)

                // Filter: near-horizontal only (within BARBELL_MAX_TILT_DEG from horizontal)
                val normalizedTilt = if (tiltDeg > 90.0) 180.0 - tiltDeg else tiltDeg
                if (normalizedTilt > BARBELL_MAX_TILT_DEG) continue

                val lineLen = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (lineLen < minLineLenFilter) continue

                // Compute distance from line midpoint to wrist anchor in global coords
                val midX = (x1 + x2) / 2f + roiLeft
                val midY = (y1 + y2) / 2f + roiTop
                val distToWrist = sqrt(
                    ((midX - wristAnchorX) * (midX - wristAnchorX) +
                     (midY - wristAnchorY) * (midY - wristAnchorY)).toDouble()
                )

                // Score: prefer longer lines close to wrist
                val score = lineLen * 0.5 - distToWrist

                if (score > bestScore) {
                    bestScore = score
                    bestLine = DetectedLine(
                        x1 = x1 + roiLeft,
                        y1 = y1 + roiTop,
                        x2 = x2 + roiLeft,
                        y2 = y2 + roiTop,
                        centerX = midX,
                        centerY = midY,
                        angle = lineAngle.toFloat(),
                        length = lineLen,
                        confidence = (1.0f - (distToWrist.toFloat() / (rdist * 2f)).coerceIn(0f, 1f)).coerceAtLeast(0.3f)
                    )
                }
            }

            if (bestLine != null) {
                logger.trace(TAG, "Line detection found barbell line: center=(${bestLine.centerX}, ${bestLine.centerY}), length=${bestLine.length}")
            }

            return bestLine
        } catch (e: Exception) {
            logger.error(TAG, "Line detection failed", e)
            return null
        } finally {
            rgbaMat.release()
            grayMat.release()
            grayROI?.release()
            edges.release()
            dilated.release()
            lines.release()
            kernel?.release()
        }
    }

    /**
     * Looser circle detection for side-view videos where the barbell shaft isn't visible.
     * Uses relaxed HoughCircles parameters and lower circularity threshold to catch
     * weight plates at more varied angles and sizes.
     */
    fun detectCirclesLoose(bitmap: Bitmap): List<DetectedCircle> {
        if (!isInitialized) {
            if (!initialize()) return emptyList()
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
                LOOSE_DP,
                LOOSE_MIN_DIST,
                LOOSE_PARAM1,
                LOOSE_PARAM2,
                LOOSE_MIN_RADIUS,
                LOOSE_MAX_RADIUS
            )

            val result = mutableListOf<DetectedCircle>()
            if (circles.cols() > 0) {
                for (i in 0 until circles.cols()) {
                    val data = circles[0, i] ?: continue
                    val x = data[0].toFloat()
                    val y = data[1].toFloat()
                    val radius = data[2].toFloat()

                    if (x < 0 || y < 0) continue
                    if (radius < LOOSE_MIN_RADIUS_FILTER || radius > LOOSE_MAX_RADIUS_FILTER) continue

                    val circularity = validateCircularity(grayMat, x.toDouble(), y.toDouble(), radius.toDouble())
                    if (circularity < LOOSE_MIN_CIRCULARITY) continue

                    result += DetectedCircle(
                        x = x,
                        y = y,
                        radius = radius,
                        confidence = 1.0f
                    )
                }
            }

            logger.trace(TAG, "Loose detection found ${result.size} circles")
            return result
        } catch (e: Exception) {
            logger.error(TAG, "Loose circle detection failed", e)
            return emptyList()
        } finally {
            rgbaMat.release()
            grayMat.release()
            blurredMat.release()
            circles.release()
        }
    }

    /**
     * Detect weight plates using contour detection + ellipse fitting.
     * More robust than HoughCircles for real gym videos where plates appear as ellipses.
     *
     * @param bitmap The frame bitmap to analyze
     * @param handLandmarks Optional hand landmarks for proximity scoring
     * @return List of detected plate candidates sorted by confidence
     */
    fun detectPlatesByContour(
        bitmap: Bitmap,
        handLandmarks: Map<Int, PoseOverlayLandmark>?
    ): List<DetectedCircle> {
        if (!isInitialized) {
            if (!initialize()) return emptyList()
        }

        val rgbaMat = Mat()
        val grayMat = Mat()
        val blurredMat = Mat()
        val edges = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()

        try {
            Utils.bitmapToMat(bitmap, rgbaMat)
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            // Blur to reduce noise
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 1.5)

            // Adaptive threshold works better than Canny for varying lighting
            Imgproc.adaptiveThreshold(
                blurredMat, edges,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                11,  // blockSize
                2.0  // C constant
            )

            // Morphological close to fill gaps
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            // Find contours
            Imgproc.findContours(edges, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val handCenter = handLandmarks?.let { computeHandCenter(it) }
            val candidates = mutableListOf<DetectedCircle>()

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)

                // Filter by area (too small = noise, too large = background)
                if (area < CONTOUR_MIN_AREA || area > CONTOUR_MAX_AREA) continue

                // Need at least 5 points to fit ellipse
                if (contour.rows() < 5) continue

                // Fit ellipse
                val contour2f = MatOfPoint2f()
                contour.convertTo(contour2f, CvType.CV_32FC2)
                val rotatedRect = Imgproc.fitEllipse(contour2f)
                contour2f.release()

                // Extract ellipse properties
                val centerX = rotatedRect.center.x.toFloat()
                val centerY = rotatedRect.center.y.toFloat()
                val width = rotatedRect.size.width
                val height = rotatedRect.size.height

                // Filter by aspect ratio (should be roughly circular)
                val aspectRatio = if (width > height) width / height else height / width
                if (aspectRatio > MAX_ELLIPSE_ASPECT_RATIO) continue

                // Filter by size
                val avgRadius = ((width + height) / 4.0).toFloat()  // average radius
                if (avgRadius < PLATE_MIN_RADIUS || avgRadius > PLATE_MAX_RADIUS) continue

                // Score by proximity to hands
                val distToHand = if (handCenter != null) {
                    distance(centerX, centerY, handCenter.first, handCenter.second)
                } else {
                    0f
                }

                candidates += DetectedCircle(
                    x = centerX,
                    y = centerY,
                    radius = avgRadius,
                    confidence = 1.0f / (1.0f + distToHand / 100f)  // closer to hand = higher confidence
                )
            }

            // Sort by confidence (proximity to hands) and return top candidates
            return candidates.sortedByDescending { it.confidence }.take(MAX_PLATE_CANDIDATES)

        } catch (e: Exception) {
            logger.error(TAG, "Contour-based plate detection failed", e)
            return emptyList()
        } finally {
            rgbaMat.release()
            grayMat.release()
            blurredMat.release()
            edges.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    /**
     * Hybrid barbell detection orchestrator.
     *
     * 1. Try line-based detection first (primary method, for front views where bar is visible)
     * 2. Fall back to contour-based plate detection (for side views where only plates are visible)
     * 3. Return null if neither method finds anything
     */
    fun detectBarbellHybrid(
        bitmap: Bitmap,
        handLandmarks: Map<Int, PoseOverlayLandmark>?
    ): BarbellDetectionResult? {
        if (!isInitialized) {
            if (!initialize()) return null
        }

        // Primary: line detection
        val line = detectBarbellLine(bitmap, handLandmarks)
        if (line != null) {
            logger.trace(TAG, "Hybrid detection: line found")
            return BarbellDetectionResult.Line(line)
        }

        // Fallback: contour-based plate detection (more robust than HoughCircles)
        val plates = detectPlatesByContour(bitmap, handLandmarks)
        if (plates.isNotEmpty()) {
            logger.trace(TAG, "Hybrid detection: contour fallback found ${plates.size} plates")
            return BarbellDetectionResult.Circle(plates.first())  // Return best candidate
        }

        return null
    }

    companion object {
        private const val TAG = "BarbellDetectionService"

        // HoughCircles parameters (original, tight)
        private const val DP = 1.2
        private const val MIN_DIST = 50.0
        private const val PARAM1 = 100.0
        private const val PARAM2 = 55.0
        private const val MIN_RADIUS = 15
        private const val MAX_RADIUS = 150

        // Post-detection filters (original)
        private const val MIN_RADIUS_FILTER = 12f
        private const val MAX_RADIUS_FILTER = 160f

        // Circularity threshold (original)
        private const val MIN_CIRCULARITY = 0.7

        // Hand proximity threshold in pixels for weight plate candidate filtering
        private const val HAND_PROXIMITY_THRESHOLD = 200f

        // --- Line detection constants (ported from Python prototype) ---
        private const val BARBELL_MAX_TILT_DEG = 25.0
        private const val LINE_ROI_PAD_X_RATIO = 1.2f
        private const val LINE_ROI_PAD_Y_RATIO = 0.8f
        private const val LINE_MIN_LENGTH_RATIO = 0.2f
        private const val LINE_HOUGH_THRESHOLD = 30
        private const val LINE_MAX_LINE_GAP = 20.0
        private const val LINE_CANNY_LOW = 50.0
        private const val LINE_CANNY_HIGH = 150.0

        // --- Loose circle detection constants ---
        private const val LOOSE_DP = 1.4
        private const val LOOSE_MIN_DIST = 30.0
        private const val LOOSE_PARAM1 = 80.0
        private const val LOOSE_PARAM2 = 40.0
        private const val LOOSE_MIN_RADIUS = 10
        private const val LOOSE_MAX_RADIUS = 200
        private const val LOOSE_MIN_RADIUS_FILTER = 8f
        private const val LOOSE_MAX_RADIUS_FILTER = 220f
        private const val LOOSE_MIN_CIRCULARITY = 0.5

        // --- Contour-based plate detection constants ---
        private const val CONTOUR_MIN_AREA = 500.0      // Minimum contour area in pixels
        private const val CONTOUR_MAX_AREA = 50000.0     // Maximum contour area in pixels
        private const val MAX_ELLIPSE_ASPECT_RATIO = 2.0 // Max width/height ratio for ellipse
        private const val PLATE_MIN_RADIUS = 15f         // Min average radius
        private const val PLATE_MAX_RADIUS = 200f        // Max average radius
        private const val MAX_PLATE_CANDIDATES = 5       // Max candidates to return
    }
}
