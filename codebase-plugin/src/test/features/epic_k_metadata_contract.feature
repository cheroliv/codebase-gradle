@epic_k
Feature: EPIC K — Contrat metadata.json inter-borough (format pivot)
  As a borough consumer (engine N3)
  I want to validate a metadata.json before consuming a producer's artefact
  So that I never process incompatible or corrupted data silently

  Rule: Every producer writes metadata.json alongside its AsciiDoc artefact.
        Every consumer calls MetadataValidator.validate() before processing.

  Background:
    Given a temporary directory for test artefacts

  # --- Scénarios de succès ---

  Scenario: Valid SPG metadata passes validation
    Given a file "metadata.json" with content:
      """
      {"type":"SPG","source":"manhattan","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"deepseek-v4-pro","dependencies":["queens","graphify"],"sessions":24}
      """
    When I validate "metadata.json" expecting type "SPG" and version "1.0"
    Then the validation result is VALID
    And the parsed metadata type is "SPG"
    And the parsed metadata source is "manhattan"
    And the parsed metadata sessions count is 24

  Scenario: Valid Plan metadata passes validation without version check
    Given a file "plan-metadata.json" with content:
      """
      {"type":"Plan","source":"codebase","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"deepseek-v4-pro","dependencies":["queens","graphify"],"epics":3,"totalPoints":21,"classification":"complexe","estimatedSessions":"3-5"}
      """
    When I validate "plan-metadata.json" expecting type "Plan"
    Then the validation result is VALID
    And the parsed metadata classification is "complexe"
    And the parsed metadata epics count is 3

  Scenario: Same major version is accepted (1.5 vs 1.0 consumer)
    Given a file "metadata.json" with content:
      """
      {"type":"SPG","source":"manhattan","version":"1.5","generatedAt":"2026-05-18T19:00:00Z","model":"deepseek-v4-pro","dependencies":[],"sessions":24}
      """
    When I validate "metadata.json" expecting version "1.0"
    Then the validation result is VALID
    And isVersionCompatible between "1.5" and "1.0" is TRUE

  # --- Scénarios d'échec ---

  Scenario: Missing file returns invalid with clear reason
    Given no file "missing-metadata.json" exists
    When I validate "missing-metadata.json"
    Then the validation result is INVALID
    And the reason contains "introuvable"

  Scenario: Malformed JSON returns invalid with clear reason
    Given a file "corrupted-metadata.json" with content:
      """
      {ceci n'est pas du JSON valide}
      """
    When I validate "corrupted-metadata.json"
    Then the validation result is INVALID
    And the reason contains "illisible"

  Scenario: Version mismatch returns invalid (major 2 vs consumer 1)
    Given a file "metadata.json" with content:
      """
      {"type":"SPG","source":"manhattan","version":"2.0","generatedAt":"2026-05-18T19:00:00Z","model":"deepseek-v4-pro","dependencies":[],"sessions":24}
      """
    When I validate "metadata.json" expecting version "1.0"
    Then the validation result is INVALID
    And the reason contains "Version incompatible"
    And isVersionCompatible between "2.0" and "1.0" is FALSE

  Scenario: Type mismatch returns invalid
    Given a file "metadata.json" with content:
      """
      {"type":"SPG","source":"manhattan","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"deepseek-v4-pro","dependencies":[],"sessions":24}
      """
    When I validate "metadata.json" expecting type "SPD"
    Then the validation result is INVALID
    And the reason contains "Type inattendu"

  Scenario: Blank dependency returns invalid
    Given a file "metadata.json" with content:
      """
      {"type":"SPG","source":"manhattan","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"deepseek-v4-pro","dependencies":["   ","graphify"],"sessions":24}
      """
    When I validate "metadata.json"
    Then the validation result is INVALID
    And the reason contains "Dépendance vide"
