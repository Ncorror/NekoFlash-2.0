package ru.forum.adbfastboottool

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdbKeyStorePersistenceTest {
    @get:Rule val tempFolder = TemporaryFolder()

    @Test fun ownerKeyIsPublishedOnlyAfterFullWriteAndSurvivesRestart() {
        val folder = tempFolder.newFolder("adb-auth")
        val messages = mutableListOf<String>()
        val first = AdbKeyStore(folder) { messages += it }
        val originalPublic = first.publicKeyPayload()
        val originalTokenSignature = first.signToken(ByteArray(20) { it.toByte() })
        val persisted = folder.resolve("adbkey.pk8")
        val originalPrivateBytes = persisted.readBytes()
        assertTrue(originalPrivateBytes.isNotEmpty())
        assertFalse(folder.listFiles().orEmpty().any { it.name.endsWith(".tmp") })

        val second = AdbKeyStore(folder) { messages += it }
        assertArrayEquals(originalPublic, second.publicKeyPayload())
        assertArrayEquals(originalTokenSignature, second.signToken(ByteArray(20) { it.toByte() }))
        assertArrayEquals(originalPrivateBytes, persisted.readBytes())
        assertEquals(1, messages.count { "Created a new app ADB RSA key" in it })
    }

    @Test fun orphanedStagingFileIsNotMistakenForCommittedIdentity() {
        val folder = tempFolder.newFolder("stale-stage")
        val leftover = folder.resolve("adbkey-stale.tmp")
        leftover.writeText("aborted-write-do-not-publish")
        val saved = AdbKeyStore(folder) { }.publicKeyPayload()
        assertTrue(folder.resolve("adbkey.pk8").exists())
        assertTrue(leftover.exists()) // Forensic evidence: never delete unrelated files.
        assertArrayEquals(saved, AdbKeyStore(folder) { }.publicKeyPayload())
    }

    @Test fun corruptExistingKeyIsPreservedRatherThanSilentlyRotated() {
        val folder = tempFolder.newFolder("bad-primary")
        val primary = folder.resolve("adbkey.pk8")
        val corrupted = "NOT-BASE64-TRUNCATED-KEY"
        primary.writeText(corrupted)
        val message = try {
            AdbKeyStore(folder) { }.getOrCreateKeyPair()
            throw AssertionError("Corrupt stored key must fail closed")
        } catch (error: IOException) {
            requireNotNull(error.message)
        }
        assertTrue(message.contains("preserved"))
        assertEquals(corrupted, primary.readText())
        assertFalse(folder.resolve("adbkey.pub").exists())
    }
}
