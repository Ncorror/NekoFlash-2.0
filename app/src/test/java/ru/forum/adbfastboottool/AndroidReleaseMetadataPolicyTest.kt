package ru.forum.adbfastboottool

import org.junit.Assert.*
import org.junit.Test

class AndroidReleaseMetadataPolicyTest {
    @Test fun acceptsCurrentAndroidGetpropOutput() {
        assertEquals("14", AndroidReleaseMetadataPolicy.parse("14\n", true, 0))
        assertEquals("16", AndroidReleaseMetadataPolicy.parse(" 16 \n", true, 0))
    }

    @Test fun rejectsMissingFailedAndUntrustedRelease() {
        assertNull(AndroidReleaseMetadataPolicy.parse("", true, 0))
        assertNull(AndroidReleaseMetadataPolicy.parse("14", false, 0))
        assertNull(AndroidReleaseMetadataPolicy.parse("14", true, 1))
        assertNull(AndroidReleaseMetadataPolicy.parse("14\n15", true, 0))
        assertNull(AndroidReleaseMetadataPolicy.parse("not-android", true, 0))
    }

    @Test fun acceptsLegacyMissingExitWhenCaptureSucceeds() {
        assertEquals("12", AndroidReleaseMetadataPolicy.parse("12\n", true, null))
    }
}
