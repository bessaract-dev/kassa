package dev.bessaract.kassa.core.backup

// Маленький JSON без зависимостей — только для файла резервной копии.
// Объект → Map<String, Any?> (порядок ключей сохраняется), массив → List<Any?>, число → Double, ещё String / Boolean / null.

class JsonException(message: String) : Exception(message)

object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (!p.end()) throw p.err("лишние символы в конце")
        return v
    }

    fun write(v: Any?, indent: Boolean = true): String = StringBuilder().also { write(it, v, if (indent) 0 else -1) }.toString()

    private fun write(sb: StringBuilder, v: Any?, level: Int) {
        val nl = if (level >= 0) "\n" + " ".repeat(level + 1) else ""
        val close = if (level >= 0) "\n" + " ".repeat(level) else ""
        val next = if (level >= 0) level + 1 else -1
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v)
            is Double -> sb.append(if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString())
            is Number -> write(sb, v.toDouble(), level)
            is String -> string(sb, v)
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                v.entries.forEachIndexed { i, (k, x) ->
                    if (i > 0) sb.append(',')
                    sb.append(nl); string(sb, k.toString()); sb.append(':'); write(sb, x, next)
                }
                sb.append(close).append('}')
            }
            is List<*> -> {
                if (v.isEmpty()) { sb.append("[]"); return }
                sb.append('[')
                v.forEachIndexed { i, x -> if (i > 0) sb.append(','); sb.append(nl); write(sb, x, next) }
                sb.append(close).append(']')
            }
            else -> throw IllegalArgumentException("не JSON: ${v::class}")
        }
    }

    private fun string(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        sb.append('"')
    }

    /** Глубже не бывает у копии; ограничение защищает от переполнения стека на «[[[[…»". */
    private const val MAX_DEPTH = 64

    private class Parser(val s: String) {
        var i = 0
        var depth = 0
        fun end() = i >= s.length
        fun err(msg: String) = JsonException("$msg (позиция $i)")
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            if (end()) throw err("неожиданный конец")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw err("неожиданный символ '$c'")
            }
        }
        fun word(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) throw err("ожидалось $w"); i += w.length; return v }
        fun num(): Double {
            val m = Regex("""-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?""").matchAt(s, i) ?: throw err("плохое число")
            i += m.value.length
            return m.value.toDouble()
        }
        fun str(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (end()) throw err("строка не закрыта")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (end()) throw err("строка не закрыта")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw err("плохой \\u")
                                sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: throw err("плохой \\u")); i += 4
                            }
                            else -> throw err("плохой escape")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun enter() { if (++depth > MAX_DEPTH) throw err("слишком глубокая вложенность") }
        fun obj(): Map<String, Any?> {
            enter()
            i++; ws()
            val m = LinkedHashMap<String, Any?>()
            if (!end() && s[i] == '}') { i++; depth--; return m }
            while (true) {
                ws(); if (end() || s[i] != '"') throw err("ожидался ключ")
                val k = str(); ws()
                if (end() || s[i] != ':') throw err("ожидалось ':'"); i++; ws()
                m[k] = value(); ws()
                if (end()) throw err("объект не закрыт")
                when (s[i++]) { ',' -> continue; '}' -> { depth--; return m }; else -> throw err("ожидалось ',' или '}'") }
            }
        }
        fun arr(): List<Any?> {
            enter()
            i++; ws()
            val l = ArrayList<Any?>()
            if (!end() && s[i] == ']') { i++; depth--; return l }
            while (true) {
                ws(); l.add(value()); ws()
                if (end()) throw err("массив не закрыт")
                when (s[i++]) { ',' -> continue; ']' -> { depth--; return l }; else -> throw err("ожидалось ',' или ']'") }
            }
        }
    }
}
