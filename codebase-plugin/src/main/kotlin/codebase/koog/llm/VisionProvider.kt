package codebase.koog.llm

/**
 * Provider contract for vision-based OCR — suspend function accepting raw image bytes.
 *
 * Implemented by [GeminiVisionProvider] (real) and [FakeVisionProvider] (test).
 */
interface VisionProvider {
    suspend fun processImage(
        imageBytes: ByteArray,
        mimeType: String,
        language: String = "fr",
        model: String = "gemini-2.5-flash",
        maxTokens: Int = 8192
    ): String
}
