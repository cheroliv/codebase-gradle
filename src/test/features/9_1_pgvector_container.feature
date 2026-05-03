# EPIC 9.1 — Ephemeral pgvector container for codebase-gradle RAG tests
# Pattern: TestContainer + Living Documentation (inspired by plantuml-gradle RagPipelineSteps)

Feature: Ephemeral pgvector infrastructure for RAG tests

  As a codebase-gradle developer
  I want to start a PostgreSQL container with pgvector automatically
  So that RAG tests are reproducible without PostgreSQL installed on the host machine

  Scenario: Start an ephemeral pgvector container
    When I start a pgvector container
    Then the container is running
    And the vector extension is available
    And I can create a table with a vector(384) column

  Scenario: Automatic container stop after test
    When I start a pgvector container
    When I stop the container
    Then the container is not running
