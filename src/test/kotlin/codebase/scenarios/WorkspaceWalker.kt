package codebase.scenarios

data class WorkspaceFile(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val extension: String
)

class WorkspaceWalker(private val rootDir: java.io.File) {

    private val skipDirs = setOf("build", ".git", ".gradle", "node_modules", ".kotlin")

    fun walk(): List<WorkspaceFile> {
        val results = mutableListOf<WorkspaceFile>()
        walkRecursive(rootDir, results)
        return results.sortedBy { it.filePath }
    }

    private fun walkRecursive(dir: java.io.File, results: MutableList<WorkspaceFile>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name !in skipDirs && !file.name.startsWith(".")) {
                    walkRecursive(file, results)
                }
            } else {
                val ext = file.name.substringAfterLast('.', "")
                results.add(
                    WorkspaceFile(
                        fileName = file.name,
                        filePath = file.path,
                        fileSize = file.length(),
                        extension = ext
                    )
                )
            }
        }
    }
}
