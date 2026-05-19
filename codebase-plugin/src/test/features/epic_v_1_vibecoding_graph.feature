@epic_v_1
Feature: VibecodingGraph — Graphe koog de vibecoding
  As a vibecoding agent developer
  I want to define a koog graph connecting buildContext → classify → plan → executeTools → sendToolResult
  So that the agent execution loop is declaratively structured and verifiable via Mermaid

  Background:
    Given a VibecodingGraph World is initialized

  Scenario: Le graphe compile et contient les 5 nœuds
    When the VibecodingGraph is instantiated
    Then the graph contains node "buildContext"
    And the graph contains node "classify"
    And the graph contains node "plan"
    And the graph contains node "executeTools"
    And the graph contains node "sendToolResult"

  Scenario: Le graphe génère un diagramme Mermaid valide
    When the VibecodingGraph is instantiated
    Then the Mermaid diagram contains "buildContext"
    And the Mermaid diagram contains "classify"
    And the Mermaid diagram contains "plan"
    And the Mermaid diagram contains "executeTools"
    And the Mermaid diagram contains "sendToolResult"
    And the Mermaid diagram contains "-->"

  Scenario: Le graphe construit le systemPrompt dynamique
    When the VibecodingGraph is instantiated
    Then the systemPrompt contains "intention"
    And the systemPrompt contains "plan"
    And the systemPrompt contains "workspaceRoot"
    And the systemPrompt contains "dryRun"

  Scenario: L'état de vibecoding tracke les itérations correctement
    Given a VibecodingState with maxActions 3
    When iteration 2 is reached
    Then the state is NOT final
    When iteration 3 is reached
    Then the state IS final

  Scenario: Le graphe réutilise KoogAugmentedContextGraph pour buildContext/classify/plan
    When the VibecodingGraph is instantiated
    Then the augmented context graph is not null
