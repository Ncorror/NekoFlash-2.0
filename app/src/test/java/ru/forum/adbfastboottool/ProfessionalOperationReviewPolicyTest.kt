package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalOperationReviewPolicyTest {
    private val review = ProfessionalOperationReviewPolicy

    @Test fun eraseAndFormatAlwaysGetOneExplicitReview() {
        assertTrue(review.requiresReview(ProfessionalOperationReviewPolicy.Action.ERASE, listOf("userdata")))
        assertTrue(review.requiresReview(ProfessionalOperationReviewPolicy.Action.FORMAT, listOf("metadata")))
        assertTrue(review.requiresReview(ProfessionalOperationReviewPolicy.Action.ERASE, listOf("persist")))
        assertTrue(review.requiresReview(ProfessionalOperationReviewPolicy.Action.FORMAT, listOf("custom_partition")))
    }

    @Test fun quickRecoveryAndBootStayFastForProfessionalWorkflow() {
        for (target in listOf("boot", "boot_a", "init_boot_b", "vendor_boot", "recovery")) {
            assertFalse(review.requiresReview(ProfessionalOperationReviewPolicy.Action.QUICK_FLASH, listOf(target)))
        }
        assertFalse(review.requiresReview(ProfessionalOperationReviewPolicy.Action.QUICK_FLASH, listOf("boot"), "a"))
    }

    @Test fun criticalAndAllSlotFlashRequireExplicitReview() {
        for (target in listOf("userdata", "metadata", "persist", "system_a", "super", "custom_partition")) {
            assertTrue(target, review.isCritical(target))
            assertTrue(target, review.requiresReview(ProfessionalOperationReviewPolicy.Action.QUICK_FLASH, listOf(target)))
        }
        assertTrue(review.requiresReview(ProfessionalOperationReviewPolicy.Action.QUICK_FLASH, listOf("boot"), "all"))
        assertTrue(review.requiresReview(ProfessionalOperationReviewPolicy.Action.QUICK_FLASH, listOf("vbmeta")))
        assertTrue(review.requiresReview(ProfessionalOperationReviewPolicy.Action.QUICK_FLASH, listOf("modem_a")))
        assertTrue(review.requiresReview(ProfessionalOperationReviewPolicy.Action.MASS_FLASH, listOf("vbmeta")))
        assertFalse(review.isCritical("vbmeta"))

    }

    @Test fun queueReviewIsOneCheckpointNotOnePerPartition() {
        assertFalse(review.requiresReview(ProfessionalOperationReviewPolicy.Action.MASS_FLASH, listOf("boot")))
        assertTrue(review.requiresReview(ProfessionalOperationReviewPolicy.Action.MASS_FLASH, listOf("boot", "recovery")))
        assertTrue(review.requiresReview(ProfessionalOperationReviewPolicy.Action.MASS_FLASH, listOf("userdata")))
    }

    @Test fun selectedPartitionAndSlotStayVerbatim() {
        assertEquals("boot  [slot: all]", review.displayTarget("boot", "all"))
        assertEquals("boot_a", review.displayTarget("boot_a", "b"))
        assertEquals("custom_X  [slot: a]", review.displayTarget("custom_X", "a"))
        assertEquals("persist", review.displayTarget("persist", null))
    }
}
