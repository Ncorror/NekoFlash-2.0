package ru.forum.adbfastboottool

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleAutoFollowPolicyTest {
    @Test fun newOutputAtBottomFollowsWhenNothingSelected() {
        assertTrue(ConsoleAutoFollowPolicy.followOutput(false, true, false))
    }

    @Test fun selectionAtBottomPreventsAutomaticJump() {
        assertFalse(ConsoleAutoFollowPolicy.followOutput(false, true, true))
    }

    @Test fun browsingOlderLogsNeverAutoFollows() {
        assertFalse(ConsoleAutoFollowPolicy.followOutput(false, false, false))
        assertFalse(ConsoleAutoFollowPolicy.followOutput(false, false, true))
    }

    @Test fun explicitReopenStillShowsLatestOutput() {
        assertTrue(ConsoleAutoFollowPolicy.followOutput(true, false, false))
        assertTrue(ConsoleAutoFollowPolicy.followOutput(true, true, false))
    }
}
