package codebase.ocr

/**
 * Moteur OCR par défaut — non configuré.
 *
 * Lève une erreur explicite si appelé. L'utilisateur doit injecter
 * un vrai moteur (GeminiVisionEngine en production, FakeOcrEngine en test).
 */
class NoOpOcrEngine : OcrEngine {
    override fun process(input: String, language: String, model: String, maxTokens: Int): String {
        throw IllegalStateException(
            "OcrEngine non configuré. Injectez GeminiVisionEngine (production) " +
            "ou FakeOcrEngine (test) via ocrTask.ocrEngine = ..."
        )
    }
}
