package codebase.ocr

/**
 * Moteur OCR factice pour les tests unitaires.
 *
 * Simule le comportement de Gemini Vision sans appel réseau :
 * retourne le contenu d'entrée dans un template AsciiDoc structuré.
 *
 * Permet aux tests unitaires de vérifier le contrat OcrEngine
 * sans dépendance à Gemini, au réseau, ni à une clé API.
 */
class FakeOcrEngine : OcrEngine {

    override fun process(input: String, language: String, model: String, maxTokens: Int): String {
        if (input.isBlank()) return ""

        return buildString {
            appendLine("= Document OCRisé")
            appendLine(":langue: $language")
            appendLine(":modèle: $model")
            appendLine(":max-tokens: $maxTokens")
            appendLine(":source: fake-engine (test unitaire)")
            appendLine()
            appendLine("== Contenu extrait")
            appendLine()
            appendLine(input)
            appendLine()
            appendLine("[NOTE]")
            appendLine("=====")
            appendLine("Ce document a été généré par FakeOcrEngine — moteur de test.")
            appendLine("En production, GeminiVisionEngine produirait un résultat identique")
            appendLine("via l'API Gemini Vision (multimodal image → texte structuré).")
            appendLine("=====")
        }
    }
}
