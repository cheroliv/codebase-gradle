package codebase.ocr

import java.io.File

/**
 * Moteur d'OCR — abstraction injectable.
 *
 * Pattern Clean Architecture : `fun interface` — identique à `LlmProvider`.
 * Permet d'injecter `FakeOcrEngine` en test, `GeminiVisionEngine` en production.
 *
 * Contrat : retourne du texte structuré au format AsciiDoc (format pivot workspace).
 */
fun interface OcrEngine {
    /**
     * Extrait le texte d'une image ou d'un document scanné.
     *
     * @param input Contenu textuel ou bytes de l'image (pour le fake engine) ;
     *              en production, les bytes de l'image/PDF.
     * @param language Langue source (fr, en, auto)
     * @param model Modèle Gemini à utiliser
     * @param maxTokens Limite de tokens pour la requête
     * @return Texte structuré au format AsciiDoc
     */
    fun process(input: String, language: String, model: String, maxTokens: Int): String
}
