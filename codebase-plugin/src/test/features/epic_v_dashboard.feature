@epic_v @epic_v_8
Feature: DashboardTask Gradle — vibecoding dashboard CLI
  As a codebase-gradle developer
  I want to validate that the vibecodingDashboard task is registered by CodebasePlugin
  and fails gracefully without a ConnectionFactory
  So that the dashboard CLI can be exposed safely in any project

  Background:
    Given the codebase plugin is applied to a Gradle project

  Scenario: Dashboard task is registered with correct metadata
    When I look up the "vibecodingDashboard" task
    Then the task group is "tracking"
    And the task description contains "Dashboard"

  Scenario: Dashboard task throws without ConnectionFactory
    When I execute the "vibecodingDashboard" task without connection factory
    Then a vibecoding dashboard IllegalStateException is thrown
    And the vibecoding dashboard error contains "ConnectionFactory"
