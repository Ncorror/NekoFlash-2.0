package ru.forum.adbfastboottool

/**
 * Интерпретирует результат установки только по данным, которые Recovery уже записало
 * после завершения sideload-потока. DONEDONE сюда намеренно не относится: это лишь
 * завершение host-side потока передачи, а не install result.
 */
object RecoveryInstallVerifier {

    enum class Verdict { SUCCESS, FAILED, UNKNOWN }

    data class LogSource(
        val path: String,
        val text: String
    )

    data class Result(
        val verdict: Verdict,
        val message: String,
        val source: String? = null,
        val evidence: String? = null
    )

    private data class Event(
        val index: Int,
        val verdict: Verdict,
        val message: String,
        val evidence: String,
        val authoritative: Boolean = false
    )

    /** Historical files alone never establish which attempt produced a result. */
    fun evaluate(sources: List<LogSource>): Result = Result(
        Verdict.UNKNOWN,
        "Recovery logs are not bound to the current Sideload attempt",
        source = sources.firstOrNull()?.path
    )

    /** Called only for bytes appended after a verified same-boot log boundary. */
    fun evaluateCurrentAttempt(appended: String): Result {
        // AOSP logger prefixes lines with monotonic timestamps; the byte/hash
        // checkpoint, not these timestamps, proves freshness.
        val log = appended.replace(Regex("""(?m)^\[\s*\d+\.\d+\]\s?"""), "")
        fun unknown() = Result(Verdict.UNKNOWN, "No single completed Sideload session for this attempt")
        val operations = Regex("""(?im)^\s*I:operation_start:\s*['"]([^'"]+)['"]\s*$""")
            .findAll(log).toList()
        val sideloadStarts = Regex("""(?im)^\s*(?:Starting (?:ADB )?sideload|Now send the package you want to apply)[^\n]*""")
            .findAll(log).toList()
        val start = if (operations.isNotEmpty()) {
            // Multiple operations, including a later non-Sideload install, are ambiguous.
            if (operations.size != 1 || !operations[0].groupValues[1].equals("Sideload", true) ||
                sideloadStarts.size > 1) return unknown()
            operations[0].range.first
        } else {
            if (sideloadStarts.size != 1) return unknown()
            sideloadStarts[0].range.first
        }
        val session = log.substring(start)
        val packageStarts = Regex("""(?im)^\s*Installing (?:zip file|update|package)[^\n]*""")
            .findAll(session).count()
        if (packageStarts > 1) return unknown()
        return evaluateSource(LogSource("/tmp/recovery.log (current attempt)", session)) ?: unknown()
    }

    private fun evaluateSource(source: LogSource): Result? {
        val text = source.text
        if (text.isBlank()) return null

        val events = mutableListOf<Event>()

        Regex("""(?im)^\s*Install from ADB complete(?:\s*\(status:\s*(-?\d+)\))?\.?\s*$""")
            .findAll(text).forEach { match ->
            val status = if (match.groupValues[1].isEmpty()) 0 else match.groupValues[1].toIntOrNull() ?: return@forEach
            val verdict = if (status == 0) Verdict.SUCCESS else Verdict.FAILED
            events += Event(
                index = match.range.first,
                verdict = verdict,
                message = if (status == 0) {
                    "Recovery confirmed successful installation: status=0"
                } else {
                    "Recovery finished installation with an error: status=$status"
                },
                evidence = lineAt(text, match.range.first),
                authoritative = true
            )
        }

        Regex("""(?i)Updater process ended with ERROR:\s*(-?\d+)""").findAll(text).forEach { match ->
            val code = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (code != 0) {
                events += Event(
                    index = match.range.first,
                    verdict = Verdict.FAILED,
                    message = "Updater finished with ERROR: $code",
                    evidence = contextualEvidence(text, match.range.first)
                )
            }
        }

        // TWRP/OrangeFox record the final action status separately from DONEDONE.
        // Accept it only inside the current recognized Sideload session.
        if (isSideloadSession(text)) {
            Regex("""(?im)^\s*I:operation_end\s*-\s*status=(-?\d+)\s*$""")
                .findAll(text)
                .forEach { match ->
                    val status = match.groupValues[1].toIntOrNull() ?: return@forEach
                    events += Event(
                        index = match.range.first,
                        verdict = if (status == 0) Verdict.SUCCESS else Verdict.FAILED,
                        message = if (status == 0) {
                            "Recovery confirmed successful Sideload completion: operation_end status=0"
                        } else {
                            "Recovery finished Sideload with an error: operation_end status=$status"
                        },
                        evidence = contextualEvidence(text, match.range.first),
                        authoritative = true
                    )
                }
        }

        failurePatterns.forEach { regex ->
            regex.findAll(text).forEach { match ->
                events += Event(
                    index = match.range.first,
                    verdict = Verdict.FAILED,
                    message = failureMessage(match.value),
                    evidence = contextualEvidence(text, match.range.first)
                )
            }
        }

        // A final Recovery status outranks intermediate updater/script output.
        val lastEvent = events.filter { it.authoritative }.maxByOrNull { it.index }
            ?: events.maxByOrNull { it.index } ?: return null
        return Result(
            verdict = lastEvent.verdict,
            message = lastEvent.message,
            source = source.path,
            evidence = lastEvent.evidence
        )
    }

    private fun isSideloadSession(text: String): Boolean =
        sideloadSessionPatterns.any { it.containsMatchIn(text) }

    private fun failureMessage(value: String): String {
        val normalized = value.lowercase()
        return when {
            "product.img" in normalized -> "Recovery reports a product.img write error"
            "installation aborted" in normalized -> "Recovery aborted installation"
            "error installing zip" in normalized -> "Recovery could not install ZIP"
            else -> "Recovery reports an install error"
        }
    }

    private fun lineAt(text: String, index: Int): String {
        val start = text.lastIndexOf('\n', (index - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', index).let { if (it < 0) text.length else it }
        return text.substring(start, end).trim().take(500)
    }

    private fun contextualEvidence(text: String, index: Int): String {
        val lines = text.lines()
        var consumed = 0
        var target = 0
        for ((i, line) in lines.withIndex()) {
            val next = consumed + line.length + 1
            if (index < next) {
                target = i
                break
            }
            consumed = next
        }
        val from = (target - 2).coerceAtLeast(0)
        val to = (target + 2).coerceAtMost(lines.lastIndex)
        return lines.subList(from, to + 1)
            .joinToString(" | ") { it.trim() }
            .take(1200)
    }

    private val sideloadSessionPatterns = listOf(
        Regex("""(?im)^\s*Now send the package you want to apply"""),
        Regex("""(?im)^\s*Starting ADB sideload"""),
        Regex("""(?im)^\s*Starting sideload"""),
        Regex("""(?im)^\s*I:operation_start:\s*['"]Sideload['"]\s*$"""),
        Regex("""(?im)^\s*Starting ADB Sideload"""),
        Regex("""(?im)^\s*Installing zip file"""),
        Regex("""(?im)^\s*Installing ZIP file""")
    )

    private val failurePatterns = listOf(
        Regex("(?i)Error installing zip file"),
        Regex("(?i)Installation aborted"),
        Regex("(?i)E:Error in .*\\(status\\s+[-]?\\d+\\)"),
        Regex("(?i)failed[^\\n]{0,160}product\\.img"),
        Regex("(?i)product\\.img[^\\n]{0,160}failed"),
        Regex("(?i)script aborted[^\\n]*")
    )

}
