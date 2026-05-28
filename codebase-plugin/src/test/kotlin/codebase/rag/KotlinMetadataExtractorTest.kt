package codebase.rag

import org.junit.jupiter.api.Test
import kotlin.test.*

class KotlinMetadataExtractorTest {

    private val extractor = KotlinMetadataExtractor("test-repo")

    @Test
    fun `extract should find package declaration`() {
        val content = """
            |package com.example.foo
            |
            |class MyService
        """.trimMargin()

        val metadata = extractor.extract("MyService.kt", content)

        assertEquals("com.example.foo", metadata.packageName)
        assertEquals("test-repo", metadata.repoName)
    }

    @Test
    fun `extract should find class name`() {
        val content = """
            |package com.example.foo
            |
            |class MyService
        """.trimMargin()

        val metadata = extractor.extract("MyService.kt", content)

        assertEquals("MyService", metadata.className)
    }

    @Test
    fun `extract should find object declaration`() {
        val content = """
            |package com.example.foo
            |
            |object MySingleton
        """.trimMargin()

        val metadata = extractor.extract("MySingleton.kt", content)

        assertEquals("MySingleton", metadata.className)
    }

    @Test
    fun `extract should find interface declaration`() {
        val content = """
            |package com.example.foo
            |
            |interface MyRepository
        """.trimMargin()

        val metadata = extractor.extract("MyRepository.kt", content)

        assertEquals("MyRepository", metadata.className)
    }

    @Test
    fun `extract should find data class`() {
        val content = """
            |package com.example.foo
            |
            |data class User(val name: String, val age: Int)
        """.trimMargin()

        val metadata = extractor.extract("User.kt", content)

        assertEquals("User", metadata.className)
    }

    @Test
    fun `extract should find sealed class`() {
        val content = """
            |package com.example.foo
            |
            |sealed class Result<out T>
        """.trimMargin()

        val metadata = extractor.extract("Result.kt", content)

        assertEquals("Result", metadata.className)
    }

    @Test
    fun `extract should fallback to filename when no class object or interface found`() {
        val content = """
            |package com.example.foo
            |
            |fun helperFunction(): String = "hello"
        """.trimMargin()

        val metadata = extractor.extract("Utils.kt", content)

        assertEquals("com.example.foo", metadata.packageName)
        assertEquals("Utils", metadata.className)
    }

    @Test
    fun `extract should return null package when no package declaration`() {
        val content = """
            |class Standalone
        """.trimMargin()

        val metadata = extractor.extract("Standalone.kt", content)

        assertNull(metadata.packageName)
        assertEquals("Standalone", metadata.className)
    }

    @Test
    fun `extract should use last class when multiple declarations exist`() {
        val content = """
            |package com.example.foo
            |
            |class FirstClass
            |class LastClass
            |object CompanionUtil
        """.trimMargin()

        val metadata = extractor.extract("File.kt", content)

        assertEquals("CompanionUtil", metadata.className)
    }

    @Test
    fun `extract should handle indented declarations`() {
        val content = """
            |    package com.indented.example
            |
            |    class IndentedService
        """.trimMargin()

        val metadata = extractor.extract("IndentedService.kt", content)

        assertEquals("com.indented.example", metadata.packageName)
        assertEquals("IndentedService", metadata.className)
    }

    @Test
    fun `extract should handle enum class`() {
        val content = """
            |package com.example.foo
            |
            |enum class Color { RED, GREEN, BLUE }
        """.trimMargin()

        val metadata = extractor.extract("Color.kt", content)

        assertEquals("Color", metadata.className)
    }

    @Test
    fun `extract should handle abstract class`() {
        val content = """
            |package com.example.foo
            |
            |abstract class BaseProcessor
        """.trimMargin()

        val metadata = extractor.extract("BaseProcessor.kt", content)

        assertEquals("BaseProcessor", metadata.className)
    }

    @Test
    fun `extract should handle open class`() {
        val content = """
            |package com.example.foo
            |
            |open class Extensible
        """.trimMargin()

        val metadata = extractor.extract("Extensible.kt", content)

        assertEquals("Extensible", metadata.className)
    }
}
