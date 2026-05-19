package codebase.scenarios

import codebase.koog.AuditEntry
import codebase.koog.ToolRegistry
import java.io.File

class VibecodingTaskWorld {
    var workspaceRoot: File = File("/tmp/vibecoding-task-test-${System.currentTimeMillis()}").also { it.mkdirs() }
    var taskRegistered: Boolean = false
    var taskType: String = ""
    var taskCompleted: Boolean = false
    var taskFailed: Boolean = false
    var failureMessage: String = ""
    var auditTrailFile: File? = null
    var auditTrailContent: String = ""
    var toolCount: Int = 0
    var toolNames: List<String> = emptyList()
    var iterationCount: Int = 0
    var stateFinished: Boolean = false
    var auditEntries: List<AuditEntry> = emptyList()
    var registry: ToolRegistry = ToolRegistry()
    var lastOutput: String = ""
    var rejectionMessage: String = ""
    var tempWorkspace: File? = null
}
