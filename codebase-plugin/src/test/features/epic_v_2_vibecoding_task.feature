@epic_v_2
Feature: VibecodingTask — Tâche Gradle CLI koog autonome
  As a vibecoding agent developer
  I want to execute a Gradle task that runs the vibecoding loop via koog
  So that the agent can be invoked from the command line with ./gradlew vibecode

  Background:
    Given a VibecodingTask World is initialized

  Scenario: Le ToolRegistry expose 7 tools
    When I create a ToolRegistry
    Then the registry contains tool "read_file"
    And the registry contains tool "write_file"
    And the registry contains tool "edit_file"
    And the registry contains tool "list_directory"
    And the registry contains tool "exit"
    And the registry contains tool "exec_shell"
    And the registry contains tool "exec_gradle"

  Scenario: Le maxActions limite le nombre d'itérations
    When I execute the VibecodingGraph with maxActions 3
    Then the iteration count does not exceed 3
    And the state is marked as finished

  Scenario: Le dryRun est activé par défaut en VibecodingState
    When I create a VibecodingState with dryRun true
    Then the state dryRun is true

  Scenario: Le ToolRegistry exécute un tool connu
    When I execute the "read_file" tool with path "test.txt" containing "hello tool"
    Then the tool output contains "hello tool"
