@epic_v_0
Feature: Custom Tools Shell — ExecShellTool et ExecGradleTool
  As a vibecoding agent developer
  I want to validate that ExecShellTool and ExecGradleTool execute shell commands safely
  So that the agent can run bash and Gradle commands with allowlist protection

  Background:
    Given a VibecodingTool World is initialized

  Scenario: ExecShellTool exécute une commande valide
    When I execute ExecShellTool with command "echo hello"
    Then the shell exit code is 0
    And the output contains "hello"

  Scenario: ExecShellTool bloque une commande blacklistée — rm -rf
    When I execute ExecShellTool with command "rm -rf /tmp/test"
    Then the shell command is rejected
    And the rejection message contains "blacklisted"

  Scenario: ExecShellTool bloque une commande blacklistée — sudo
    When I execute ExecShellTool with command "sudo echo dangerous"
    Then the shell command is rejected
    And the rejection message contains "blacklisted"

  Scenario: ExecGradleTool exécute une tâche Gradle valide
    When I execute ExecGradleTool with task "compileKotlin"
    Then the gradle exit code is 0

  Scenario: ExecGradleTool bloque une tâche destructive
    When I execute ExecGradleTool with task "clean build --refresh-dependencies"
    Then the gradle task is rejected
    And the rejection message contains "blacklisted"

  Scenario: ExecShellTool respecte le timeout
    When I execute ExecShellTool with command "sleep 2" and timeout 100 milliseconds
    Then the shell command is rejected
    And the rejection message contains "timeout"

  Scenario: ExecShellTool capture stdout et stderr
    When I execute ExecShellTool with command "echo stdout-text && echo stderr-text >&2"
    Then the output contains "stdout-text"
    And the output contains "stderr-text"
