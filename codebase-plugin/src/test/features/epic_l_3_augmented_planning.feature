@epic_l_3
Feature: Pipeline de Planification Augmentée koog + langchain4j
  As a codebase-gradle developer
  I want to validate that KoogAugmentedContextGraph executes the full pipeline buildContext → classify → plan
  So that the augmented context graph replaces the manual processState() with zero duplication

  Background:
    Given a KoogAugmentedContextGraph is instantiated

  Scenario: Exécution sans pgvector — résilience, pas de crash
    When I execute the augmented context pipeline with intention "Add dark mode toggle"
    Then the result state is not null
    And the intention is preserved in the result state
    And the classification is "simple"

  Scenario: Classification simple — intention courte
    When I execute the augmented context pipeline with intention "Fix typo in README"
    Then the result state is not null
    And the classification is "simple"

  Scenario: Classification complexe — intention cross-borough
    When I execute the augmented context pipeline with intention "Refactor cross-borough DAG N1→N2→N3 pour architecture multi-plugins distribuée"
    Then the result state is not null
    And the classification is "complexe"

  Scenario: Classification simple — intention moyenne sans mot-clé
    When I execute the augmented context pipeline with intention "Update configuration file default values"
    Then the result state is not null
    And the classification is "simple"

  Scenario: Erreur pgvector absente signalée
    When I execute the augmented context pipeline with intention "Add feature flag to codebase"
    Then the result state is not null
    And the error field indicates context is unavailable or partial

  Scenario: Diagramme Mermaid valide
    When I execute the augmented context pipeline with intention "test"
    Then a Mermaid diagram is generated

  Scenario: Intention préservée après pipeline complet
    Given a temporary workspace root "/tmp/koog-pipeline-test" is created
    When I execute the augmented context pipeline with intention "Implement caching layer" and workspace "/tmp/koog-pipeline-test"
    Then the result state is not null
    And the intention is preserved in the result state
