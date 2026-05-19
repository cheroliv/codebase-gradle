package codebase.scenarios

import java.io.File

class VibecodingToolWorld {

    val workspaceRoot: File = File("/tmp/vibecoding-tool-test").also { it.mkdirs() }
    var shellResult: String = ""
    var shellRejected: Boolean = false
    var rejectionMessage: String = ""
    var gradleResult: String = ""
    var gradleRejected: Boolean = false
    var gradleRejectionMessage: String = ""
}
