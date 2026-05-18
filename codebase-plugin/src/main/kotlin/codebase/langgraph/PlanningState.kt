package codebase.langgraph

import codebase.rag.CompositeContext
import org.bsc.langgraph4j.state.AgentState

class PlanningState(data: MutableMap<String, Any>) : AgentState(data) {
    val intention: String get() = (data()["intention"] as? String) ?: ""
    val compositeContext: CompositeContext? get() = data()["compositeContext"] as? CompositeContext
    val classification: String get() = (data()["classification"] as? String) ?: ""
    val planJson: String get() = (data()["planJson"] as? String) ?: ""
    val plan: Any? get() = data()["plan"]
    val error: String? get() = data()["error"] as? String
}
