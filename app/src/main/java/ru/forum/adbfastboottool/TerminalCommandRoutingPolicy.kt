package ru.forum.adbfastboottool

/**
 * Keeps host-shell input separate from device transports.
 * Bare host commands resolve locally outside an interactive ADB shell; while
 * that session is active, bare input stays device-side unless explicitly forced local.
 */
internal object TerminalCommandRoutingPolicy {
    private val adbDeviceDiagnostics = setOf(
        "getprop", "id", "uname", "uptime", "whoami", "df", "ps", "date",
    )

    sealed interface Route {
        data class Local(val command: String) : Route
        data class Adb(val command: String) : Route
        data class Fastboot(val command: String) : Route
        data class FastbootShell(val command: String) : Route
        data class InteractiveAdb(val raw: String, val type: String, val command: String) : Route
        data class Context(val raw: String) : Route
    }

    fun resolve(
        raw: String,
        currentContext: String,
        interactiveAdbShellActive: Boolean,
    ): Route {
        val clean = raw.trim()
        val localCommand = extractLocalCommand(clean)
        val implicitLocalCommand = extractImplicitLocalCommand(clean)
        val explicitLocal = clean == "local" ||
            (clean.startsWith("local") && clean.getOrNull(5)?.isWhitespace() == true)

        if (explicitLocal) {
            return Route.Local(localCommand ?: clean.removePrefix("local").trimStart())
        }

        val firstCommand = clean.takeWhile { !it.isWhitespace() }
        val explicitHostShell = firstCommand.equals("sh", ignoreCase = true) ||
            firstCommand.equals("bash", ignoreCase = true)
        val explicitAdb = clean.startsWith("adb ", ignoreCase = true)
        val explicitFastboot = clean.startsWith("fastboot ", ignoreCase = true)
        val explicitType = when {
            explicitAdb -> "adb"
            explicitFastboot -> "fastboot"
            else -> currentContext
        }
        val explicitCommand = if (explicitAdb || explicitFastboot) {
            clean.substringAfter(' ').trim()
        } else {
            clean
        }

        // An explicit Fastboot prefix must never be sent as text to an already
        // open interactive ADB shell. Mixed shell expressions use the existing
        // confirmed Fastboot bridge; a single Fastboot command keeps native routing.
        if (explicitFastboot) {
            return if (hasUnquotedShellControlOperator(clean)) {
                Route.FastbootShell("sh -c ${quoteForShellArgument(clean)}")
            } else {
                Route.Fastboot(explicitCommand)
            }
        }

        // `sh`/`bash` are explicit host-shell entrypoints. Do not reinterpret
        // command substitution, pipes, or other shell syntax as device input.
        if (explicitHostShell) return Route.Local(clean)

        if (explicitAdb) {
            val interactiveShellInput = explicitCommand.equals("shell", ignoreCase = true) ||
                explicitCommand.startsWith("shell ", ignoreCase = true) ||
                explicitCommand.lowercase() in INTERACTIVE_ADB_COMMANDS
            return if (interactiveAdbShellActive && interactiveShellInput) {
                Route.InteractiveAdb(clean, "adb", explicitCommand)
            } else {
                Route.Adb(explicitCommand)
            }
        }

        // Read-only Android diagnostics remain transport commands in ADB mode,
        // even while an interactive shell session is open. They must use the
        // managed ADB command path rather than being injected as raw shell text.
        if (currentContext == "adb" && firstCommand in adbDeviceDiagnostics) {
            return Route.Adb(clean)
        }

        // An open interactive ADB shell owns bare input. `local ...` and
        // explicit `sh`/`bash` entrypoints above remain host-side overrides.
        if (interactiveAdbShellActive) {
            return Route.InteractiveAdb(clean, explicitType, explicitCommand)
        }

        if (implicitLocalCommand != null) return Route.Local(implicitLocalCommand)

        return when (currentContext) {
            "adb" -> Route.Adb(clean)
            "fastboot" -> Route.Fastboot(clean)
            else -> Route.Context(clean)
        }
    }

    internal fun hasUnquotedShellControlOperator(command: String): Boolean {
        var quote: Char? = null
        var escaping = false
        var index = 0
        while (index < command.length) {
            val character = command[index]
            when {
                escaping -> escaping = false
                character == '\\' -> escaping = true
                quote != null -> if (character == quote) quote = null
                character == '\'' || character == '"' -> quote = character
                command.startsWith("&&", index) || command.startsWith("||", index) ||
                    command.startsWith("\$(", index) -> return true
                character == ';' || character == '|' || character == '>' || character == '<' || character == '`' -> return true
            }
            index++
        }
        return false
    }

    private val INTERACTIVE_ADB_COMMANDS = setOf(
        "shell-stop", "shell-exit", "shell-ctrl-c", "shell-interrupt", "shell-ctrl-d", "shell-eof",
    )

    private fun quoteForShellArgument(value: String): String =
        "'${value.replace("'", "'\\''")}'"
}
