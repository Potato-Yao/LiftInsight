package com.potato.liftinsight.plan.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.common.logging.RecordingAppLogger
import com.potato.liftinsight.training.data.CreateMetaHistoryRequest
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.UpdateImportedVideoMetadataRequest
import com.potato.liftinsight.video.imported.ImportedVideoAnalysisMode
import com.potato.liftinsight.video.imported.ImportedVideoSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainingPlanStoreImportedVideoTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var trainingPlanStore: TrainingPlanStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftInsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trainingPlanStore = TrainingPlanStore.fromDatabase(database, RecordingAppLogger())

        database.motionDao().insertMotionEntity(
            com.potato.liftinsight.training.data.MotionEntity(name = "Snatch")
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importedVideoMetadata_roundTripsCalibrationFields() {
        val historyId = trainingPlanStore.insertMetaHistory(
            CreateMetaHistoryRequest(
                date = "2026-06-02",
                rep = 3,
                rpe = 8,
                weight = 90.0,
                motionId = 1,
                videoName = "imported.mp4",
                videoSource = ImportedVideoSource.LOCAL_FILE,
                importedVideoAnalysisMode = ImportedVideoAnalysisMode.ESTIMATED
            )
        ).toInt()

        val updated = trainingPlanStore.updateImportedVideoMetadata(
            UpdateImportedVideoMetadataRequest(
                historyId = historyId,
                videoSource = ImportedVideoSource.LOCAL_FILE,
                analysisMode = ImportedVideoAnalysisMode.REFERENCE_CALIBRATED,
                referenceLabel = "Plate diameter",
                referencePixelDistance = 180.0,
                referenceDistanceMeters = 0.45
            )
        )
        val record = trainingPlanStore.getMetaHistoryRecords().single()
        val metadata = trainingPlanStore.getImportedVideoMetadata(historyId)

        assertTrue(updated)
        assertEquals(ImportedVideoSource.LOCAL_FILE, record.videoSource)
        assertEquals(ImportedVideoAnalysisMode.REFERENCE_CALIBRATED, record.importedVideoAnalysisMode)
        assertEquals("Plate diameter", record.importedReferenceLabel)
        assertEquals(180.0, record.importedReferencePixelDistance ?: Double.NaN, 0.0)
        assertEquals(0.45, record.importedReferenceDistanceMeters ?: Double.NaN, 0.0)
        assertNotNull(metadata?.calibration)
        assertEquals(0.45 / 180.0, metadata?.calibration?.metersPerPixel ?: Double.NaN, 0.0)
    }
}
