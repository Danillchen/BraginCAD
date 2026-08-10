package com.bragincad.view

internal data class DecodedMText(val plainText: String, val unsupportedCommands: Set<Char>)

internal object MTextDecoder {
    fun decode(raw: String): DecodedMText {
        val safeRaw=raw.take(MAX_INPUT_CHARS)
        val out = StringBuilder(safeRaw.length)
        val unsupported = linkedSetOf<Char>()
        var i = 0
        while (i < safeRaw.length) {
            val ch = safeRaw[i]
            if (ch == '{' || ch == '}') { i++; continue }
            if (ch != '\\') { out.append(ch); i++; continue }
            if (++i >= safeRaw.length) { out.append('\\'); break }
            val command = safeRaw[i++]
            when (command) {
                '\\' -> out.append('\\')
                '{' -> out.append('{')
                '}' -> out.append('}')
                '~' -> out.append('\u00a0')
                'P' -> out.append('\n')
                'L', 'l', 'O', 'o', 'K', 'k' -> Unit
                'S' -> {
                    val end = safeRaw.indexOf(';', i).let { if (it < 0) safeRaw.length else it }
                    val value = safeRaw.substring(i, end)
                    out.append(value.replace('#', '/').replace('^', '/'))
                    i = (end + 1).coerceAtMost(safeRaw.length)
                }
                'A', 'C', 'c', 'F', 'f', 'H', 'Q', 'T', 'W', 'p' ->
                    i = skipArgument(safeRaw, i)
                'U' -> {
                    // AutoCAD/LibreDWG commonly exposes \U+XXXX unicode escapes.
                    if (i < safeRaw.length && safeRaw[i] == '+' && i + 4 < safeRaw.length) {
                        val value = safeRaw.substring(i + 1, i + 5).toIntOrNull(16)
                        if (value != null) { out.append(value.toChar()); i += 5 } else unsupported += command
                    } else unsupported += command
                }
                else -> {
                    // Formatting commands must never leak into the drawing. Preserve an
                    // unknown escape's payload only when it has no semicolon argument.
                    unsupported += command
                    val end = safeRaw.indexOf(';', i)
                    if (end >= i) i = end + 1
                }
            }
        }
        return DecodedMText(
            out.toString().replace("%%d", "°", ignoreCase = true)
                .replace("%%p", "±", ignoreCase = true)
                .replace("%%c", "Ø", ignoreCase = true),
            unsupported
        )
    }

    private fun skipArgument(raw: String, start: Int): Int {
        val end = raw.indexOf(';', start)
        return if (end < 0) raw.length else end + 1
    }
    private const val MAX_INPUT_CHARS=1_000_000
}
