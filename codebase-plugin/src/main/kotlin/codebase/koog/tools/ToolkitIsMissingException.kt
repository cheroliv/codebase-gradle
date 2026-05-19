package codebase.koog.tools

class ToolkitIsMissingException(toolName: String?) : Exception(buildMessage(toolName)) {
    companion object {
        private fun buildMessage(toolName: String?): String {
            return if (toolName == null) "Missing tool in the registry"
            else "Toolkit is missing the tool '$toolName' in the vibecoding agent registry"
        }
    }
}
