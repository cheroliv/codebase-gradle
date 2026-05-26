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
    Then the vibecoding diagram contains "buildContext" and "executeTools" and "checkProgress"

  Scenario: Pipeline complet ne crash pas sans pgvector
    When I execute vibecoding with intention "Test resilience" and dryRun true
    Then the vibecoding result state is not null
    And vibecoding had no error
    And the vibecoding iteration count is greater than or equal to 0

  @epic_v_3
  Scenario: Path traversal outside workspaceRoot is blocked
    When I attempt vibecoding path traversal outside workspace root "/tmp"
    Then a vibecoding SecurityException is thrown
    And the vibecoding error contains "outside workspaceRoot"

  @epic_v_3
  Scenario: File larger than 10MB is rejected
    When I attempt vibecoding read of a file larger than 10 MB in workspace "/tmp"
    Then a vibecoding SecurityException is thrown
    And the vibecoding error contains "10 MB"

  @epic_v_4
  Scenario: Golden path — agent executes a multi-task plan in dryRun
    When I execute vibecoding with a 3-task plan and maxActions 5 in dryRun
    Then vibecoding had no error
    And exactly 3 vibecoding tasks are marked as executed

  @epic_v_4
  Scenario: Multi-tour — agent executes all tasks across multiple iterations
    When I execute vibecoding with a 5-task plan and maxActions 10 in dryRun
    Then vibecoding had no error
    And exactly 5 vibecoding tasks are marked as executed
    And the vibecoding iteration count is greater than or equal to 5

  @epic_v_5
  Scenario: LLM decides the next task autonomously
    Given a VibecodingGraph is initialized with Gemini fake chat model
    When the LLM receives a prompt containing "Add dark mode toggle"
    Then the LLM decides autonomously
    And the vibecoding tracking records at least 1 prompt token

  @epic_v_6 @wip
  Scenario: Ollama pool rotates instances when quota exceeded
    Given an Ollama pool with 2 instances "ollama-a" and "ollama-b" and threshold 2
    When the LLM provider is called 5 times
    Then both pool instances "ollama-a" and "ollama-b" are used
    And the quota exceeded flag is raised for instance "ollama-a"
