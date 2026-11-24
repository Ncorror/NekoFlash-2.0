package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCommandRoutingPolicyTest {
    @Test fun bareHostCommandsWinOverTheSelectedAdbOrFastbootTab() {
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("ls"),
            TerminalCommandRoutingPolicy.resolve("ls", "adb", interactiveAdbShellActive = false),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("cd \"ROM files\""),
            TerminalCommandRoutingPolicy.resolve("cd \"ROM files\"", "fastboot", interactiveAdbShellActive = false),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("pwd"),
            TerminalCommandRoutingPolicy.resolve("pwd", "adb", interactiveAdbShellActive = false),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("help"),
            TerminalCommandRoutingPolicy.resolve("help", "fastboot", interactiveAdbShellActive = false),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("ls && pwd"),
            TerminalCommandRoutingPolicy.resolve("ls && pwd", "adb", interactiveAdbShellActive = false),
        )
    }

    @Test fun explicitShellEntrypointsNeverInheritTheSelectedDeviceTransport() {
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("bash $(ls)"),
            TerminalCommandRoutingPolicy.resolve("bash $(ls)", "fastboot", false),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("sh $(find . -name '*.sh')"),
            TerminalCommandRoutingPolicy.resolve("sh $(find . -name '*.sh')", "adb", false),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("bash $(ls)"),
            TerminalCommandRoutingPolicy.resolve("bash $(ls)", "adb", true),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("bash $(ls)"),
            TerminalCommandRoutingPolicy.resolve("local bash $(ls)", "fastboot", false),
        )
    }

    @Test fun explicitAdbAndFastbootPrefixesRemainAuthoritative() {
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Adb("shell ls"),
            TerminalCommandRoutingPolicy.resolve("adb shell ls", "fastboot", interactiveAdbShellActive = false),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Adb("devices"),
            TerminalCommandRoutingPolicy.resolve("adb devices", "adb", interactiveAdbShellActive = true),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.InteractiveAdb("adb shell ls", "adb", "shell ls"),
            TerminalCommandRoutingPolicy.resolve("adb shell ls", "adb", interactiveAdbShellActive = true),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.InteractiveAdb("adb SHELL", "adb", "SHELL"),
            TerminalCommandRoutingPolicy.resolve("adb SHELL", "adb", interactiveAdbShellActive = true),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Fastboot("flash boot boot.img"),
            TerminalCommandRoutingPolicy.resolve("fastboot flash boot boot.img", "adb", interactiveAdbShellActive = false),
        )
    }

    @Test fun explicitLocalPrefixCanStillForceHostCommands() {
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("cd /storage/emulated/0"),
            TerminalCommandRoutingPolicy.resolve("local cd /storage/emulated/0", "adb", interactiveAdbShellActive = true),
        )
    }

    @Test fun ambiguousDeviceDiagnosticsKeepTheSelectedAdbContextUnlessForcedLocal() {
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Adb("getprop ro.build.version.release"),
            TerminalCommandRoutingPolicy.resolve("getprop ro.build.version.release", "adb", false),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("getprop ro.build.version.release"),
            TerminalCommandRoutingPolicy.resolve("local getprop ro.build.version.release", "adb", false),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Adb("getprop ro.build.version.release"),
            TerminalCommandRoutingPolicy.resolve("getprop ro.build.version.release", "adb", true),
        )
    }

    @Test fun activeInteractiveAdbShellOwnsBareInputButLocalPrefixRemainsAnOverride() {
        assertEquals(
            TerminalCommandRoutingPolicy.Route.InteractiveAdb("ls", "adb", "ls"),
            TerminalCommandRoutingPolicy.resolve("ls", "adb", interactiveAdbShellActive = true),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.InteractiveAdb("echo hello", "adb", "echo hello"),
            TerminalCommandRoutingPolicy.resolve("echo hello", "adb", interactiveAdbShellActive = true),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("echo hello"),
            TerminalCommandRoutingPolicy.resolve("local echo hello", "adb", interactiveAdbShellActive = true),
        )
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Local("bash $(ls)"),
            TerminalCommandRoutingPolicy.resolve("bash $(ls)", "adb", interactiveAdbShellActive = true),
        )
    }

    @Test fun explicitFastbootCommandsDoNotLeakIntoAnInteractiveAdbShell() {
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Fastboot("reboot"),
            TerminalCommandRoutingPolicy.resolve("fastboot reboot", "adb", interactiveAdbShellActive = true),
        )
    }

    @Test fun mixedFastbootCommandsUseTheExistingConfirmedScriptBridge() {
        val route = TerminalCommandRoutingPolicy.resolve(
            "fastboot flash boot boot.img && echo \"Done, now boot into ROM\"",
            "fastboot",
            interactiveAdbShellActive = false,
        )
        assertTrue(route is TerminalCommandRoutingPolicy.Route.FastbootShell)
        assertEquals(
            "sh -c 'fastboot flash boot boot.img && echo \"Done, now boot into ROM\"'",
            (route as TerminalCommandRoutingPolicy.Route.FastbootShell).command,
        )
    }

    @Test fun operatorsInsideQuotesDoNotConvertOrdinaryFastbootArgumentsIntoScripts() {
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Fastboot("oem \"label && keep\""),
            TerminalCommandRoutingPolicy.resolve(
                "fastboot oem \"label && keep\"",
                "fastboot",
                interactiveAdbShellActive = false,
            ),
        )
    }

    @Test fun explicitDevicePrefixesRemainDistinctFromLocalShellSyntax() {
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Adb("shell bash $(ls)"),
            TerminalCommandRoutingPolicy.resolve("adb shell bash $(ls)", "fastboot", false),
        )
        val fastbootChain = TerminalCommandRoutingPolicy.resolve(
            "fastboot flash boot boot.img && echo \"Done, now boot into ROM\"",
            "adb",
            false,
        )
        assertTrue(fastbootChain is TerminalCommandRoutingPolicy.Route.FastbootShell)
    }

    @Test fun adbRemoteShellChainsRemainDeviceSide() {
        assertEquals(
            TerminalCommandRoutingPolicy.Route.Adb("shell ls && echo done"),
            TerminalCommandRoutingPolicy.resolve("adb shell ls && echo done", "fastboot", false),
        )
    }
}
