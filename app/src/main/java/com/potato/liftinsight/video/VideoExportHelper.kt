package com.potato.liftinsight.video

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoExportHelper {
    private const val EXPORT_DIRECTORY = "LiftInsight"
    private const val TAG = "VideoExportHelper"

    @Volatile
    private var logger: AppLogger = AndroidAppLogger

    internal fun setLogger(logger: AppLogger) {
        this.logger = logger
    }

    suspend fun exportToGallery(context: Context, sourceFile: File): Uri? =
        withContext(Dispatchers.IO) {
            logger.debug(TAG, "exportToGallery start: sourceFile=${sourceFile.name}")

            if (!sourceFile.exists() || !sourceFile.canRead()) {
                return@withContext null
            }

            val exportFileName = buildExportFileName(sourceFile.name)
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, exportFileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/$EXPORT_DIRECTORY"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return@withContext null

            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: run {
                    resolver.delete(uri, null, null)
                    return@withContext null
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val finalValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, finalValues, null, null)
                }

                logger.debug(TAG, "exportToGallery result: uri=$uri")
                uri
            } catch (error: IOException) {
                logger.error(TAG, "Failed to export video to gallery: sourceFile=${sourceFile.name}", error)
                resolver.delete(uri, null, null)
                null
            } catch (error: SecurityException) {
                logger.error(TAG, "Security exception during video export: sourceFile=${sourceFile.name}", error)
                resolver.delete(uri, null, null)
                null
            }
        }

    suspend fun exportToGallery(context: Context, sourceFile: File, customDisplayName: String): Uri? =
        withContext(Dispatchers.IO) {
            logger.debug(TAG, "exportToGallery start: sourceFile=${sourceFile.name}, customDisplayName=$customDisplayName")

            if (!sourceFile.exists() || !sourceFile.canRead()) {
                return@withContext null
            }

            val exportFileName = "${customDisplayName}.mp4"
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, exportFileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/$EXPORT_DIRECTORY"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return@withContext null

            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: run {
                    resolver.delete(uri, null, null)
                    return@withContext null
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val finalValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, finalValues, null, null)
                }

                logger.debug(TAG, "exportToGallery result: uri=$uri")
                uri
            } catch (error: IOException) {
                logger.error(TAG, "Failed to export video to gallery: sourceFile=${sourceFile.name}", error)
                resolver.delete(uri, null, null)
                null
            } catch (error: SecurityException) {
                logger.error(TAG, "Security exception during video export: sourceFile=${sourceFile.name}", error)
                resolver.delete(uri, null, null)
                null
            }
        }

    internal fun buildExportFileName(originalName: String): String {
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) {
            originalName.substring(0, dotIndex)
        } else {
            originalName
        }

        return "${baseName}_export.mp4"
    }
}
