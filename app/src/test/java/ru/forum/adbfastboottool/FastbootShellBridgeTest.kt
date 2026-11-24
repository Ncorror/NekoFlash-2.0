package ru.forum.adbfastboottool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

class FastbootShellBridgeTest {
    @Test fun forwardsWorkingDirectoryAndArgumentBoundariesAndReturnsExitCode() {
        val received = AtomicReference<Pair<String, List<String>>?>()
        FastbootShellBridge { arguments, directory ->
            received.set(directory to arguments)
            FastbootShellBridge.Result(7, listOf("line one", "line two"))
        }.use { bridge ->
            Socket(InetAddress.getByName("127.0.0.1"), bridge.port).use { socket ->
                val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                writer.appendLine(bridge.authToken)
                writer.appendLine("/storage/emulated/0/Download/NekoFlash")
                writer.appendLine("-s")
                writer.appendLine("device-serial")
                writer.appendLine("flash")
                writer.appendLine("boot_a")
                writer.appendLine("boot image.img")
                writer.flush()
                socket.shutdownOutput()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                assertEquals("7", reader.readLine())
                assertEquals("line one", reader.readLine())
                assertEquals("line two", reader.readLine())
            }
        }
        val request = requireNotNull(received.get())
        assertEquals("/storage/emulated/0/Download/NekoFlash", request.first)
        assertEquals(listOf("-s", "device-serial", "flash", "boot_a", "boot image.img"), request.second)
    }

    @Test fun bindsOnlyToLoopback() {
        FastbootShellBridge { _, _ -> FastbootShellBridge.Result(0) }.use { bridge ->
            assertTrue(bridge.port in 1..65535)
        }
    }
    @Test fun rejectsUnauthenticatedLocalRequests() {
        FastbootShellBridge { _, _ -> error("Unauthenticated requests must never reach the callback") }.use { bridge ->
            Socket(InetAddress.getByName("127.0.0.1"), bridge.port).use { socket ->
                val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                writer.appendLine("not-the-session-token")
                writer.flush()
                socket.shutdownOutput()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                assertEquals("126", reader.readLine())
                assertTrue(reader.readLine().contains("unauthenticated"))
            }
        }
    }

}
