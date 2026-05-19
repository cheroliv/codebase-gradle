package codebase.scenarios

import java.io.File

class VibecodingGraphWorld {
    var workspaceRoot: File = File(System.getProperty("java.io.tmpdir"), "vibecoding-test-${System.currentTimeMillis()}")
        private set
    var graphResult: String = ""
    var systemPrompt: String = ""
    var state: codebase.koog.VibecodingState? = null

    init {
        workspaceRoot.mkdirs()
    }
}
