@epic_v_3
Feature: Vibecoding Security Constraints — Sandbox, dryRun, Audit Trail
  As a vibecoding agent developer
  I want ToolRegistry to prevent path traversal, respect dryRun, and maintain audit trail
  So that the agent is secure, observable, and safe to run in production

  Background:
    Given a VibecodingTask World is initialized

  Scenario: Le sandbox bloque le path traversal avec ../
    When I v3-execute the "read_file" tool with path "../etc/passwd" in workspace "/tmp/vibecoding-sandbox-cuc"
    Then the v3-tool-execution is rejected
    And the v3-rejection contains "Path traversal"

  Scenario: Le sandbox bloque un chemin absolu hors workspaceRoot
    When I v3-execute the "read_file" tool with path "/etc/passwd" in workspace "/tmp/vibecoding-sandbox-abs-cuc"
    Then the v3-tool-execution is rejected
    And the v3-rejection contains "Path traversal"

  Scenario: Le sandbox autorise un fichier à l'intérieur du workspaceRoot
    When I v3-execute the "read_file" tool with path "safe-doc.txt" containing "safe data" in workspace temp
    Then the v3-tool-execution succeeds
    And the v3-tool-output contains "safe data"

  Scenario: Le dryRun empêche write_file d'écrire sur le disque
    When I v3-execute the "write_file" tool with dryRun and path "should-not-exist.txt" content "ghost"
    Then the v3-tool-output contains "DRY RUN"
    And the file "should-not-exist.txt" does not exist on disk

  Scenario: Le dryRun empêche edit_file de modifier le fichier
    Given a file "preserve.txt" with content "original"
    When I v3-execute the "edit_file" tool with dryRun on "preserve.txt" replacing "original" with "modified"
    Then the v3-tool-output contains "DRY RUN"
    And the file "preserve.txt" still contains "original"

  Scenario: Le dryRun empêche exec_shell d'exécuter la commande
    When I v3-execute the "exec_shell" tool with dryRun and command "echo hello"
    Then the v3-tool-output contains "DRY RUN"
    And the v3-tool-output does not contain "EXIT:"

  Scenario: Le dryRun empêche exec_gradle d'exécuter la tâche
    When I v3-execute the "exec_gradle" tool with dryRun and task "build"
    Then the v3-tool-output contains "DRY RUN"

  Scenario: L'audit trail enregistre chaque exécution de tool
    Given the audit trail is cleared
    When I v3-execute tool "read_file" on "audit-target.txt" with content "audit-me"
    And I v3-execute tool "list_directory" with path "."
    When I v3-execute tool "exit"
    Then the audit trail has 3 entries
    And the audit entry 0 has tool "read_file"
    And the audit entry 1 has tool "list_directory"
    And the audit entry 2 has tool "exit"

  Scenario: L'audit trail enregistre les erreurs d'exécution
    Given the audit trail is cleared
    When I v3-execute the "read_file" tool with path "nonexistent.txt" in workspace "/tmp/vibecoding-nonexistent"
    Then the audit trail has 1 entry
    And the audit entry has error

  Scenario: L'audit trail tronque les résultats longs
    Given the audit trail is cleared
    When I v3-execute the "read_file" tool with path "big-file.txt" containing 1000 chars
    Then the audit trail has 1 entry
    And the audit entry result is at most 500 characters
