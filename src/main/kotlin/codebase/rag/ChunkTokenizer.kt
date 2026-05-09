package codebase.rag

object ChunkTokenizer {

    fun splitIntoSentenceLevelChunks(text: String, maxTokens: Int = 512, overlapTokens: Int = 50): List<String> {
        val segments = splitIntoSegments(text)
        val results = mutableListOf<String>()
        val current = StringBuilder()
        var currentTokens = 0

        for (segment in segments) {
            val segTokens = estimateTokenCount(segment)
            if (currentTokens + segTokens > maxTokens && current.isNotEmpty()) {
                results.add(current.toString().trim())
                val overlap = buildOverlap(current.toString(), overlapTokens)
                current.clear().append(overlap)
                currentTokens = estimateTokenCount(overlap)
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(segment)
            currentTokens += segTokens + 2
        }
        if (current.isNotBlank()) results.add(current.toString().trim())
        if (results.isEmpty()) results.add(text)
        return results
    }

    fun splitIntoSegments(text: String): List<String> {
        val lines = text.split("\n")
        val segments = mutableListOf<String>()
        val buf = StringBuilder()
        for (line in lines) {
            if (line.isBlank()) {
                if (buf.isNotBlank()) {
                    segments.add(buf.toString().trimEnd())
                    buf.clear()
                }
            } else {
                if (buf.isNotEmpty()) buf.append("\n")
                buf.append(line)
            }
        }
        if (buf.isNotBlank()) segments.add(buf.toString().trimEnd())
        return segments
    }

    fun buildOverlap(text: String, overlapTokens: Int): String {
        val words = text.split(Regex("\\s+"))
        val overlapWords = (overlapTokens * 0.75).toInt().coerceAtMost(words.size)
        return words.takeLast(overlapWords.coerceAtLeast(1)).joinToString(" ") + "\n\n"
    }

    fun estimateTokenCount(text: String): Int =
        (text.trim().length / 3.5).toInt().coerceAtLeast(1)
}
