@epic_v
Feature: Pipeline Vibecoding — koog autonomous loop
  As a codebase-gradle developer
  I want to validate that VibecodingGraph executes the full pipeline
  buildContext → executeAction (loop) → checkProgress → finish
  So that the vibecoding agent can autonomously execute plans

  Background:
    Given a VibecodingGraph is instantiated without augmented graph

  Scenario: Exécution en dryRun — pas d'erreur
    When I execute vibecoding with intention "Add dark mode toggle" and dryRun true
    Then the vibecoding result state is not null
    And the vibecoding dry run flag is true
    And vibecoding had no error

  Scenario: Intention préservée après exécution
    When I execute vibecoding with intention "Fix typo in README" and dryRun true
    Then the vibecoding intention "Fix typo in README" is preserved in the result state

  Scenario: Limite maxActions respectée
    When I execute vibecoding with intention "Refactor large codebase" and maxActions 3
    Then the vibecoding result state iteration is at most 3
    And the vibecoding result state is finished or final

  Scenario: MaxActions à zéro — arrêt immédiat
    When I execute vibecoding with intention "Quick fix" and maxActions 0
    Then the vibecoding result state is finished or final

  Scenario: Classification sans pgvector — mode résilient
    When I execute vibecoding with intention "Simple task"
    Then the vibecoding classification is "simple"

  Scenario: Diagramme Mermaid valide
    When I request the Mermaid diagram from vibecoding
    Then the vibecoding diagram contains "buildContext" and "executeAction" and "checkProgress"

  Scenario: Pipeline complet ne crash pas sans pgvector
    When I execute vibecoding with intention "Test resilience" and dryRun true
    Then the vibecoding result state is not null
    And vibecoding had no error
    And the vibecoding iteration count is greater than or equal to 0
