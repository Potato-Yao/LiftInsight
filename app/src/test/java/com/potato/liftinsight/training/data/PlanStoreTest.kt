package com.potato.liftinsight.training.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanStoreTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var planDao: PlanDao
    private lateinit var motionStore: MotionStore
    private lateinit var planStore: PlanStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftInsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        planDao = database.planDao()
        motionStore = MotionStore.fromDatabase(database)
        planStore = PlanStore.fromDatabase(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createPlan_persistsTrimmedPlanAndOrderedMetaPlans() {
        val snatchId = createMotion("Snatch")
        val frontSquatId = createMotion("Front Squat")

        val planId = planStore.createPlan(
            CreatePlanRequest(
                name = "  Strength Base  ",
                cyclePeriod = 7,
                currentIndex = 2,
                lastAppliedAt = 1234L,
                metaPlans = listOf(
                    CreateMetaPlanRequest(
                        motionId = frontSquatId,
                        sets = 5,
                        reps = 3,
                        intensity = 0.78,
                        weight = 105.0,
                        orderIndex = 2
                    ),
                    CreateMetaPlanRequest(
                        motionId = snatchId,
                        sets = 6,
                        reps = 2,
                        intensity = 0.85,
                        weight = 72.5,
                        orderIndex = 1
                    )
                )
            )
        )

        val storedPlan = planStore.getPlan(planId)

        assertEquals("Strength Base", storedPlan?.name)
        assertEquals(7, storedPlan?.cyclePeriod)
        assertEquals(2, storedPlan?.currentIndex)
        assertEquals(1234L, storedPlan?.lastAppliedAt)
        assertEquals(listOf(1, 2), storedPlan?.metaPlans?.map { metaPlan -> metaPlan.orderIndex })
        assertEquals(listOf("Snatch", "Front Squat"), storedPlan?.metaPlans?.map { metaPlan -> metaPlan.motionName })
        assertEquals(2, planDao.countMetaPlansForPlan(planId))
        assertEquals(0.85, storedPlan?.metaPlans?.first()?.intensity ?: Double.NaN, 0.0)
        assertEquals(72.5, storedPlan?.metaPlans?.first()?.weight ?: Double.NaN, 0.0)
    }

    @Test
    fun getPlans_returnsNamesSortedAlphabetically() {
        val snatchId = createMotion("Snatch")

        planStore.createPlan(
            CreatePlanRequest(
                name = "Technique Cycle",
                cyclePeriod = 10,
                metaPlans = listOf(
                    CreateMetaPlanRequest(
                        motionId = snatchId,
                        sets = 5,
                        reps = 2,
                        intensity = 0.75,
                        weight = 75.0,
                        orderIndex = 1
                    )
                )
            )
        )
        planStore.createPlan(
            CreatePlanRequest(
                name = "Competition Peak",
                cyclePeriod = 14
            )
        )

        val plans = planStore.getPlans()

        assertEquals(listOf("Competition Peak", "Technique Cycle"), plans.map { plan -> plan.name })
    }

    @Test
    fun updatePlan_replacesMetadataAndMetaPlans() {
        val snatchId = createMotion("Snatch")
        val cleanPullId = createMotion("Clean Pull")
        val frontSquatId = createMotion("Front Squat")
        val planId = planStore.createPlan(
            CreatePlanRequest(
                name = "Pull Block",
                cyclePeriod = 7,
                metaPlans = listOf(
                    CreateMetaPlanRequest(
                        motionId = cleanPullId,
                        sets = 4,
                        reps = 3,
                        intensity = 0.82,
                        weight = 120.0,
                        orderIndex = 1
                    )
                )
            )
        )

        val updated = planStore.updatePlan(
            PlanRecord(
                id = planId,
                name = "  Competition Peak  ",
                cyclePeriod = 14,
                currentIndex = 4,
                lastAppliedAt = 5678L,
                metaPlans = listOf(
                    MetaPlanRecord(
                        id = 0,
                        motionId = snatchId,
                        motionName = "",
                        sets = 5,
                        reps = 2,
                        intensity = 0.88,
                        weight = 82.5,
                        orderIndex = 1
                    ),
                    MetaPlanRecord(
                        id = 0,
                        motionId = frontSquatId,
                        motionName = "",
                        sets = 4,
                        reps = 3,
                        intensity = 0.8,
                        weight = 110.0,
                        orderIndex = 2
                    )
                )
            )
        )

        val storedPlan = planStore.getPlan(planId)

        assertTrue(updated)
        assertEquals("Competition Peak", storedPlan?.name)
        assertEquals(14, storedPlan?.cyclePeriod)
        assertEquals(4, storedPlan?.currentIndex)
        assertEquals(5678L, storedPlan?.lastAppliedAt)
        assertEquals(listOf("Snatch", "Front Squat"), storedPlan?.metaPlans?.map { metaPlan -> metaPlan.motionName })
        assertEquals(0.8, storedPlan?.metaPlans?.last()?.intensity ?: Double.NaN, 0.0)
        assertEquals(110.0, storedPlan?.metaPlans?.last()?.weight ?: Double.NaN, 0.0)
        assertEquals(2, planDao.countMetaPlansForPlan(planId))
    }

    @Test
    fun deletePlan_removesPlanAndCascadesMetaPlans() {
        val snatchId = createMotion("Snatch")
        val planId = planStore.createPlan(
            CreatePlanRequest(
                name = "Competition Peak",
                cyclePeriod = 7,
                metaPlans = listOf(
                    CreateMetaPlanRequest(
                        motionId = snatchId,
                        sets = 5,
                        reps = 1,
                        intensity = 0.92,
                        weight = 85.0,
                        orderIndex = 1
                    )
                )
            )
        )

        val deleted = planStore.deletePlan(planId)

        assertTrue(deleted)
        assertNull(planStore.getPlan(planId))
        assertEquals(0, planDao.countMetaPlansForPlan(planId))
        assertFalse(planStore.deletePlan(planId))
    }

    @Test(expected = IllegalArgumentException::class)
    fun createPlan_rejectsDuplicateOrderIndexes() {
        val snatchId = createMotion("Snatch")
        val cleanPullId = createMotion("Clean Pull")

        planStore.createPlan(
            CreatePlanRequest(
                name = "Duplicate Order",
                cyclePeriod = 7,
                metaPlans = listOf(
                    CreateMetaPlanRequest(
                        motionId = snatchId,
                        sets = 5,
                        reps = 2,
                        intensity = 0.8,
                        weight = 80.0,
                        orderIndex = 1
                    ),
                    CreateMetaPlanRequest(
                        motionId = cleanPullId,
                        sets = 4,
                        reps = 3,
                        intensity = 0.82,
                        weight = 110.0,
                        orderIndex = 1
                    )
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun createPlan_rejectsUnknownMotionReference() {
        planStore.createPlan(
            CreatePlanRequest(
                name = "Missing Motion",
                cyclePeriod = 7,
                metaPlans = listOf(
                    CreateMetaPlanRequest(
                        motionId = 999,
                        sets = 5,
                        reps = 2,
                        intensity = 0.8,
                        weight = 80.0,
                        orderIndex = 1
                    )
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun createPlan_rejectsNegativeIntensity() {
        val snatchId = createMotion("Snatch")

        planStore.createPlan(
            CreatePlanRequest(
                name = "Bad Intensity",
                cyclePeriod = 7,
                metaPlans = listOf(
                    CreateMetaPlanRequest(
                        motionId = snatchId,
                        sets = 5,
                        reps = 2,
                        intensity = -0.1,
                        weight = 80.0,
                        orderIndex = 1
                    )
                )
            )
        )
    }

    private fun createMotion(name: String): Int {
        return motionStore.createMotion(CreateMotionRequest(name = name))
    }

    @Test(expected = IllegalArgumentException::class)
    fun createPlan_rejectsOrderIndexGreaterThanCyclePeriod() {
        val snatchId = createMotion("Snatch")

        planStore.createPlan(
            CreatePlanRequest(
                name = "Cycle Overflow",
                cyclePeriod = 2,
                metaPlans = listOf(
                    CreateMetaPlanRequest(
                        motionId = snatchId,
                        sets = 5,
                        reps = 2,
                        intensity = 0.8,
                        weight = 80.0,
                        orderIndex = 3
                    )
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun updatePlan_rejectsCurrentIndexGreaterThanCyclePeriod() {
        val planId = planStore.createPlan(
            CreatePlanRequest(
                name = "Strength Base",
                cyclePeriod = 7,
                currentIndex = 1
            )
        )

        planStore.updatePlan(
            PlanRecord(
                id = planId,
                name = "Strength Base",
                cyclePeriod = 3,
                currentIndex = 4,
                lastAppliedAt = 0L
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun createPlan_rejectsZeroCurrentIndex() {
        planStore.createPlan(
            CreatePlanRequest(
                name = "Bad Current Day",
                cyclePeriod = 7,
                currentIndex = 0
            )
        )
    }
}


