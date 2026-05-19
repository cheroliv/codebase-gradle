package codebase.scenarios

import codebase.koog.AuditEntry
import codebase.koog.ToolRegistry
import java.io.File

class VibecodingIntegrationWorld {
    lateinit var tempProjectDir: File
    val sourceFiles: MutableMap<String, String> = mutableMapOf()
    val originalContents: MutableMap<String, String> = mutableMapOf()
    var taskError: String? = null
    var taskCompleted: Boolean = false
    var auditTrailFile: File? = null
    var auditTrailContent: String = ""
    var auditEntries: List<AuditEntry> = emptyList()
    var registry: ToolRegistry = ToolRegistry()
    var rejectionMessage: String = ""
    var lastOutput: String = ""
    lateinit var generatedFile: File
}
