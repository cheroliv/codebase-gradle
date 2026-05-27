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

  @epic_v_6
  Scenario: Erreur récupérable déclenche un retry avec LLM
    Given a VibecodingGraph is initialized with fake LLM for error recovery
    And the fake LLM suggests the next response "retry with different approach"
    When I execute vibecoding with a 1-task failing plan and maxRetries 3
    Then the vibecoding result has at least 2 iterations
    And the vibecoding retry count is at least 1
    And vibecoding had no error

  @epic_v_6
  Scenario: MaxRetries épuisé — abandon avec erreur fatale
    Given a VibecodingGraph is initialized with fake LLM for error recovery
    And the fake LLM suggests the next response "try again"
    When I execute vibecoding with a 1-task failing plan and maxRetries 1
    Then the vibecoding result has an error
    And the vibecoding error contains "MaxRetriesExhausted"

  @epic_v_6
  Scenario: Timeout est une erreur fatale — pas de retry
    Given a VibecodingGraph is instantiated without augmented graph
    When I execute vibecoding with a 5-task plan already timed out and maxRetries 3
    Then the vibecoding result has an error
    And the vibecoding retry count is 0

  @epic_v_7
  Scenario: Resume session reconstructs VibecodingState from SessionRecord
    Given a SessionRecord with id "resume-001" and intention "Fix typo in README" and maxActions 20
    When the vibecoding graph resumes that session
    Then the resumed vibecoding intention contains "resume-001" and "Fix typo in README"
    And the resumed vibecoding workspace root is "/tmp/test-resume"
    And the resumed vibecoding maxActions is 20
    And the resumed vibecoding iteration is 0

  @epic_v_7
  Scenario: Resume finished session returns finished state
    Given a SessionRecord with id "fin-999" and intention "Finished task" and maxActions 10 and finished true
    When the vibecoding graph resumes that session
    Then the resumed vibecoding state is final
