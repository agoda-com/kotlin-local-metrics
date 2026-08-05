package io.agodadev.localmetrics.ktor

internal object StartupMetricsJson {
    fun encode(payload: StartupMetricsPayload): String = buildString {
        append('{')
        field("id", payload.id)
        field("metricsVersion", payload.metricsVersion)
        field("userName", payload.userName)
        field("cpuCount", payload.cpuCount)
        field("hostname", payload.hostname)
        field("platform", payload.platform)
        field("os", payload.os)
        field("timeTaken", payload.timeTaken)
        nullableField("branch", payload.branch)
        nullableField("commitSha", payload.commitSha)
        field("type", payload.type)
        field("projectName", payload.projectName)
        nullableField("repository", payload.repository)
        nullableField("repositoryName", payload.repositoryName)
        field("date", payload.date)
        mapField("tags", payload.tags)
        finalField("isDebuggerAttached", payload.isDebuggerAttached)
        append('}')
    }

    private fun StringBuilder.field(name: String, value: String) {
        appendQuoted(name)
        append(':')
        appendQuoted(value)
        append(',')
    }

    private fun StringBuilder.nullableField(name: String, value: String?) {
        appendQuoted(name)
        append(':')
        if (value == null) append("null") else appendQuoted(value)
        append(',')
    }

    private fun StringBuilder.field(name: String, value: Number) {
        appendQuoted(name)
        append(':')
        append(value)
        append(',')
    }

    private fun StringBuilder.mapField(name: String, values: Map<String, String>) {
        appendQuoted(name)
        append(":{")
        values.toSortedMap().entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            appendQuoted(key)
            append(':')
            appendQuoted(value)
        }
        append("},")
    }

    private fun StringBuilder.finalField(name: String, value: Boolean) {
        appendQuoted(name)
        append(':')
        append(value)
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}
