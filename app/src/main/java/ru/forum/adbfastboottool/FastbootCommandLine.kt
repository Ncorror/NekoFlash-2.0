package ru.forum.adbfastboottool

/** Keep the original spelling of OEM arguments while removing host options. */
internal object FastbootCommandLine {
    data class Token(val value: String, val literal: String)

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val value = StringBuilder()
        var start = -1
        var quote: Char? = null
        var escaping = false
        fun finish(end: Int) {
            if (start >= 0) tokens += Token(value.toString(), input.substring(start, end))
            start = -1
            value.clear()
        }
        input.forEachIndexed { index, ch ->
            if (start < 0 && !ch.isWhitespace()) start = index
            when {
                escaping -> { value.append(ch); escaping = false }
                ch == '\\' -> escaping = true
                quote != null -> if (ch == quote) quote = null else value.append(ch)
                ch == '\'' || ch == '"' -> quote = ch
                ch.isWhitespace() -> finish(index)
                else -> value.append(ch)
            }
        }
        require(quote == null && !escaping) { "Unclosed quote or trailing escape in Fastboot command" }
        finish(input.length)
        return tokens
    }
}
