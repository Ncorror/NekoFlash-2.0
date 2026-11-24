package ru.forum.adbfastboottool

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdbTerminalTransferTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var activity: MainActivity
    private lateinit var workspace: File

    @Before fun attachParser() {
        RobolectricStorageFixture.installPrimaryExternalDirectory()
        activity = Robolectric.buildActivity(MainActivity::class.java).get()
        workspace = temporary.newFolder("NekoFlash")
        MainActivity::class.java.getDeclaredField("workspacePath").apply { isAccessible = true }.set(activity, workspace)
        MainActivity::class.java.getDeclaredField("viewModel").apply { isAccessible = true }
            .set(activity, DeviceViewModel(RuntimeEnvironment.getApplication()))
    }

    @Test fun pushUsesEverySourceAndTheLastArgumentAsDestination() {
        val first = File(workspace, "first file.txt").apply { writeText("first") }
        val directory = File(workspace, "second folder").apply { check(mkdir()) }
        val third = File(workspace, "third.txt").apply { writeText("third") }

        val action = parse("push \"${first.name}\" \"${directory.name}\" ${third.name} '/sdcard/Download/batch files/'")

        assertEquals(listOf(first, directory, third), field(action, "localFiles"))
        assertEquals("/sdcard/Download/batch files/", field(action, "remotePath"))
    }

    @Test fun singleFilePushStillAppendsTheNameToATrailingSlash() {
        val source = File(workspace, "single file.txt").apply { writeText("source") }

        val action = parse("push '${source.name}' /sdcard/Download/")

        assertEquals(listOf(source), field(action, "localFiles"))
        assertEquals("/sdcard/Download/${source.name}", field(action, "remotePath"))
    }

    @Test fun unavailablePushSourceStaysInTheBatchForAnIndependentResult() {
        val missing = File(workspace, "missing.txt")
        val present = File(workspace, "present.txt").apply { writeText("present") }

        val action = parse("push ${missing.name} ${present.name} /sdcard/Download")

        assertEquals(listOf(missing, present), field(action, "localFiles"))
    }

    @Test fun pullKeepsAllRemoteSourcesAndTheFinalLocalDirectory() {
        val directory = File(workspace, "saved files").apply { check(mkdir()) }
        val first = "/sdcard/Download/first file.txt"
        val second = "/sdcard/Download/folder/"

        val action = parse("pull '$first' $second '${directory.name}/'")

        assertEquals(listOf(first, second), field(action, "remotePaths"))
        assertEquals(directory, field(action, "localFile"))
    }

    @Test fun singleSourcePullStillUsesTheWorkspaceDefaultAndExplicitFile() {
        val source = "/sdcard/Download/file.txt"

        val defaultAction = parse("pull $source")
        val explicitAction = parse("pull $source 'local file.txt'")

        assertEquals(listOf(source), field(defaultAction, "remotePaths"))
        assertEquals(File(workspace, "file.txt"), field(defaultAction, "localFile"))
        assertEquals(File(workspace, "local file.txt"), field(explicitAction, "localFile"))
    }

    private fun parse(command: String): Any = requireNotNull(
        MainActivity::class.java.getDeclaredMethod("parseAdbCommand", String::class.java)
            .apply { isAccessible = true }.invoke(activity, command)
    )

    private fun field(action: Any, name: String): Any? =
        action.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(action)
}
