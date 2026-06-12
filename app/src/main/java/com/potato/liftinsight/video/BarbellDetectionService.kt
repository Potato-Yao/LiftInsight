package com.potato.liftinsight.video

import android.graphics.Bitmap
import com.potato.liftinsight.common.logging.AppLogger
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

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

    companion object {
        private const val TAG = "BarbellDetectionService"

        // HoughCircles parameters
        private const val DP = 1.2
        private const val MIN_DIST = 50.0
        private const val PARAM1 = 100.0
        private const val PARAM2 = 30.0
        private const val MIN_RADIUS = 10
        private const val MAX_RADIUS = 200

        // Post-detection filters
        private const val MIN_RADIUS_FILTER = 8f
        private const val MAX_RADIUS_FILTER = 300f
    }
}
