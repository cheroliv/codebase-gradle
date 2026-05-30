@ocr @epic_ocr
Feature: OCR — Extraction de texte assistée IA

  Scénarios de test pour la tâche Gradle `ocrDocument`.
  Utilise FakeOcrEngine (pas d'appel réseau, pas de clé API).
  En production, GeminiVisionEngine remplacera FakeOcrEngine.

  @unit
  Scenario: OCR with French text produces structured AsciiDoc
    Given an OCR test file "scan-sample.txt" with text "Ceci est un document test."
    When I OCR "scan-sample.txt" in French
    Then the OCR result for "scan-sample" exists
    And the OCR result for "scan-sample" contains "= Document OCRisé"

  @unit
  Scenario: OCR with English text includes correct language metadata
    Given an OCR test file "english-doc.txt" with text "This is an English document."
    When I OCR "english-doc.txt" in English
    Then the OCR result for "english-doc" exists
    And the OCR result for "english-doc" contains ":langue: en"

  @unit
  Scenario: OCR of empty file still produces output
    Given an OCR test file "empty-doc.txt" with text ""
    When I OCR "empty-doc.txt" in French
    Then the OCR result for "empty-doc" exists

  @unit
  Scenario: Task is registered by CodebasePlugin
    Given the codebase plugin is applied
    When I check for task "ocrDocument"
    Then task "ocrDocument" should be registered
    And task "ocrDocument" should be in group "collect"

  @unit
  Scenario: DSL output format respected
    Given an OCR test file "fmt.txt" with text "Hello world."
    When I OCR "fmt.txt" in French with format "markdown"
    Then the OCR result for "fmt" ends with ".md"
