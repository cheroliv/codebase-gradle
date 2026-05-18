package codebase.langgraph

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.bsc.langgraph4j.action.NodeAction

class FormatNode : NodeAction<PlanningState> {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun apply(state: PlanningState): Map<String, Any> {
        if (state.planJson.isBlank()) {
            return mapOf(
                "error" to "FormatNode: empty planJson, nothing to parse"
            )
        }

        return try {
            val cleanJson = extractJson(state.planJson)
            val plan: Plan = mapper.readValue(cleanJson)
            mapOf("plan" to (plan as Any))
        } catch (e: Exception) {
            mapOf(
                "error" to "FormatNode: failed to parse JSON — ${e.message}"
            )
        }
    }

    private fun extractJson(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1)
        }
        return cleaned
    }
}
