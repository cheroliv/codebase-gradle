package codebase.rag

fun main() {
    val jdbcUrl = System.getenv("PGVECTOR_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/codebase_rag"
    val user = System.getenv("PGVECTOR_USER") ?: "codebase"
    val password = System.getenv("PGVECTOR_PASSWORD") ?: "codebase"

    println("indexCodebase — connecting to pgvector at ${jdbcUrl.replace(Regex("password=.*"), "password=***")}")

    val store = VectorStore(jdbcUrl, user, password)
    store.initSchema()

    val rootDir = System.getenv("CODEBASE_ROOT_DIR")?.let { java.io.File(it) } ?: java.io.File(".")
    val walker = codebase.walker.WorkspaceWalker(rootDir)
    val files = walker.walk().filter { it.extension in listOf("kt", "adoc", "kts") }

    println("Walking ${rootDir.absolutePath}: found ${files.size} indexable files")

    val metadataExtractor = KotlinMetadataExtractor(repoName = rootDir.name)
    var totalChunks = 0
    for (wf in files) {
        val file = java.io.File(wf.filePath)
        val text = try {
            file.readText()
        } catch (e: Exception) {
            System.err.println("Cannot read ${wf.filePath}: ${e.message}")
            continue
        }
        val chunks = ChunkTokenizer.splitIntoSentenceLevelChunks(text)
        val metadata = if (wf.extension == "kt") {
            metadataExtractor.extract(wf.filePath, text)
        } else {
            KotlinMetadata(packageName = null, className = null, repoName = rootDir.name)
        }
        store.insertDocument(wf.fileName, wf.filePath, wf.fileSize, chunks, metadata.packageName, metadata.className, metadata.repoName)
        totalChunks += chunks.size
        println("  ${wf.fileName}: ${chunks.size} chunks${if (metadata.packageName != null) " (pkg=${metadata.packageName})" else ""}")
    }

    println("Total: ${files.size} documents, $totalChunks chunks")

    if (totalChunks > 0) {
        val allRecords = store.fetchAllChunks()
        println("Computing embeddings for ${allRecords.size} chunks via ONNX AllMiniLmL6V2...")
        val pipeline = EmbeddingPipeline(store)
        pipeline.embedAll(allRecords)
        println("Embeddings computed and stored")
    }

    println("indexCodebase complete — ${store.countDocuments()} documents, ${store.countChunks()} chunks indexed")
}
