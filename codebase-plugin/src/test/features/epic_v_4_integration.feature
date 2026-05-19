@epic_v_4
Feature: Vibecoding Integration Validation — End-to-end vibecode Gradle task
  As a vibecoding developer
  I want the vibecode Gradle task to work end-to-end on a temporary project
  So that I can verify sandbox + dryRun + audit trail in an integrated Gradle environment

  Background:
    Given a temporary Gradle project with Kotlin source files

  Scenario: vibecode task writes audit.jsonl even when LLM is unavailable
    When I execute the vibecode task with intention "add hello world" and dryRun true
    Then the audit trail file "build/vibecoding/audit.jsonl" exists
    And the v4-audit trail contains text "session_complete"
    And the v4-audit trail content is non-empty

  Scenario: vibecode task with dryRun never mutates project files
    When I execute the vibecode task with intention "refactor all files" and dryRun true
    Then all project source files are unchanged

  Scenario: vibecode sandbox blocks path traversal in end-to-end execution
    Given the vibecode project has a .env secret file
    When the ToolRegistry attempts "../../.env" path traversal in the project
    Then the v4-path traversal request is rejected

  Scenario: vibecode audit trail records session_complete with required fields
    When I execute the vibecode task with intention "read and list project" and dryRun true
    Then the v4-audit trail has at least 1 entries
    And the v4-audit entry at index 0 has field "action"
    And the v4-audit entry at index 0 has field "dryRun" set to true
    And the v4-audit entry at index 0 has field "timestamp"

  Scenario: vibecode with real write_file in non-dryRun writes file on disk
    When I execute a non-dryRun write_file to "build/generated/hello.txt" with content "hello world"
    Then the file "build/generated/hello.txt" exists in the project
    And the v4-generated file contains "hello world"
    And the v4-audit trail has 1 entry with tool "write_file"
