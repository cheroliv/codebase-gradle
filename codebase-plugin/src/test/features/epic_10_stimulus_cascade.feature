@epic10 @stimulus
Feature: EPIC 10 STIMULUS — DilutionExecutor injection into root documents

  Background:
    Given a temporary workspace directory for STIMULUS tests

  @unit
  Scenario: S1 — Dilute a VISION section into an existing target document
    Given the target document "WORKSPACE_VISION.adoc" already contains the section "== Stimuli dilues"
    When a VISION section is diluted into "WORKSPACE_VISION.adoc" in section "== Session du Test"
    Then the target document contains the section "=== Section de test"
    And the target document contains dilution metadata
    And a safety backup was created

  @unit
  Scenario: S2 — Dilute a section into a document without traceability table
    Given the target document "WHAT_THE_GAMES_BEEN_MISSING.adoc" does not contain a traceability table
    When a VISION section is diluted into "WHAT_THE_GAMES_BEEN_MISSING.adoc" in section "== Nouveau Test"
    Then the target document contains the traceability table "=== Stimuli diluted in this document"

  @unit
  Scenario: S3 — DRY RUN does not modify the original file
    Given the target document "WORKSPACE_ORGANIZATION.adoc" has initial content
    When a VISION section is diluted in DRY RUN mode into "WORKSPACE_ORGANIZATION.adoc"
    Then the target document keeps its initial content intact

  @unit
  Scenario: S4 — Detection of active stimuli in workspace
    Given a test workspace with 3 .adoc stimulus files and 2 already diluted documents
    When the StimulusDetector scans the workspace
    Then 3 active stimuli are detected
    And no stimulus is stale - all modified recently

  @unit
  Scenario: S5 — Detection of stale stimuli (> 2 days)
    Given a test workspace with 1 recent stimulus and 1 stimulus that is 5 days old
    When the StimulusDetector detects stale stimuli
    Then 1 stale stimulus is detected
    And 2 active stimuli are detected in total
