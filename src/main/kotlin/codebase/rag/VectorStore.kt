package codebase.rag

import java.sql.DriverManager

class VectorStore(
    private val jdbcUrl: String,
    private val jdbcUser: String,
    private val jdbcPassword: String
) {
    fun initSchema() {
        connection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE EXTENSION IF NOT EXISTS vector")
                stmt.execute("DROP TABLE IF EXISTS chunks CASCADE")
                stmt.execute("DROP TABLE IF EXISTS documents CASCADE")
                stmt.execute("""
                    CREATE TABLE documents (
                        id BIGSERIAL PRIMARY KEY,
                        file_name TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        file_size BIGINT NOT NULL,
                        chunk_count INTEGER NOT NULL,
                        package_name TEXT,
                        class_name TEXT,
                        repo_name TEXT,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                    )
                """.trimIndent())
                stmt.execute("""
                    CREATE TABLE chunks (
                        id BIGSERIAL PRIMARY KEY,
                        document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
                        chunk_index INTEGER NOT NULL,
                        chunk_text TEXT NOT NULL,
                        token_count INTEGER,
                        embedding vector(384),
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                    )
                """.trimIndent())
            }
        }
    }

    fun insertDocument(
        fileName: String,
        filePath: String,
        fileSize: Long,
        chunks: List<String>,
        packageName: String? = null,
        className: String? = null,
        repoName: String? = null
    ): Long {
        connection().use { conn ->
            val docId: Long
            conn.prepareStatement(
                "INSERT INTO documents (file_name, file_path, file_size, chunk_count, package_name, class_name, repo_name) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id"
            ).use { stmt ->
                stmt.setString(1, fileName)
                stmt.setString(2, filePath)
                stmt.setLong(3, fileSize)
                stmt.setInt(4, chunks.size)
                stmt.setString(5, packageName)
                stmt.setString(6, className)
                stmt.setString(7, repoName)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    docId = rs.getLong(1)
                }
            }
            conn.prepareStatement(
                "INSERT INTO chunks (document_id, chunk_index, chunk_text, token_count) VALUES (?, ?, ?, ?)"
            ).use { stmt ->
                chunks.forEachIndexed { index, chunkText ->
                    stmt.setLong(1, docId)
                    stmt.setInt(2, index)
                    stmt.setString(3, chunkText)
                    stmt.setInt(4, ChunkTokenizer.estimateTokenCount(chunkText))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            return docId
        }
    }

    fun updateEmbedding(chunkId: Long, vectorStr: String) {
        connection().use { conn ->
            conn.prepareStatement("UPDATE chunks SET embedding = ?::vector WHERE id = ?").use { stmt ->
                stmt.setString(1, vectorStr)
                stmt.setLong(2, chunkId)
                stmt.executeUpdate()
            }
        }
    }

    fun fetchAllChunks(): List<Pair<Long, String>> {
        val records = mutableListOf<Pair<Long, String>>()
        connection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT id, chunk_text FROM chunks ORDER BY id")
                while (rs.next()) {
                    records.add(rs.getLong("id") to rs.getString("chunk_text"))
                }
            }
        }
        return records
    }

    fun querySimilar(queryVectorStr: String, topK: Int = 5): List<QueryResult> {
        connection().use { conn ->
            conn.prepareStatement("""
                SELECT c.id, c.chunk_text, d.file_name,
                       1 - (c.embedding <=> ?::vector) AS similarity
                FROM chunks c
                JOIN documents d ON c.document_id = d.id
                WHERE c.embedding IS NOT NULL
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
            """.trimIndent()).use { stmt ->
                stmt.setString(1, queryVectorStr)
                stmt.setString(2, queryVectorStr)
                stmt.setInt(3, topK)
                return stmt.executeQuery().use { rs ->
                    val results = mutableListOf<QueryResult>()
                    while (rs.next()) {
                        results.add(
                            QueryResult(
                                chunkId = rs.getLong("id"),
                                text = "(${rs.getString("file_name")}) ${rs.getString("chunk_text")}",
                                similarity = rs.getDouble("similarity")
                            )
                        )
                    }
                    results
                }
            }
        }
    }

    fun countDocuments(): Int {
        connection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM documents")
                rs.next()
                return rs.getInt(1)
            }
        }
    }

    fun countChunks(): Int {
        connection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM chunks")
                rs.next()
                return rs.getInt(1)
            }
        }
    }

    fun hasOrphanChunks(): Boolean {
        connection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM chunks c LEFT JOIN documents d ON c.document_id = d.id WHERE d.id IS NULL"
                )
                rs.next()
                return rs.getInt(1) > 0
            }
        }
    }

    fun allEmbeddingsNonNull(): Boolean {
        connection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM chunks WHERE embedding IS NULL")
                rs.next()
                return rs.getInt(1) == 0
            }
        }
    }

    fun allEmbeddingsDimension(expectedDim: Int): Boolean {
        connection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM chunks WHERE vector_dims(embedding) != $expectedDim")
                rs.next()
                return rs.getInt(1) == 0
            }
        }
    }

    fun findByPackage(packageName: String): List<DocRecord> {
        connection().use { conn ->
            conn.prepareStatement(
                "SELECT id, file_name, file_path, file_size, package_name, class_name, repo_name FROM documents WHERE package_name = ?"
            ).use { stmt ->
                stmt.setString(1, packageName)
                return stmt.executeQuery().use { rs ->
                    val results = mutableListOf<DocRecord>()
                    while (rs.next()) {
                        results.add(
                            DocRecord(
                                id = rs.getLong("id"),
                                fileName = rs.getString("file_name"),
                                filePath = rs.getString("file_path"),
                                fileSize = rs.getLong("file_size"),
                                packageName = rs.getString("package_name"),
                                className = rs.getString("class_name"),
                                repoName = rs.getString("repo_name")
                            )
                        )
                    }
                    results
                }
            }
        }
    }

    private fun connection() = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)
}

data class DocRecord(
    val id: Long,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val packageName: String? = null,
    val className: String? = null,
    val repoName: String? = null
)

data class QueryResult(
    val chunkId: Long,
    val text: String,
    val similarity: Double
)
