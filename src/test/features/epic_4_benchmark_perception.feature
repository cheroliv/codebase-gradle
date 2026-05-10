@epic4 @wip
Feature: EPIC 4 - Benchmark de perception spatiale LLM

  As a workspace developer
  I want to validate the benchmark protocol and fixtures
  So that the spatial perception measurement is accurate and reproducible

  Scenario: Benchmark config contains correct token thresholds
    Given the benchmark protocol is loaded
    Then the thresholds contain values 10K, 30K, 60K, 100K and 128K tokens
    And the thresholds list has 5 entries

  Scenario: Benchmark config defines 5 incremental scenarios
    Given the benchmark protocol is loaded
    Then the scenarios list has 5 entries
    And the scenarios include BASELINE, RAG_ONLY, RAG_GRAPHIFY_LOCAL, RAG_GRAPHIFY_WORKSPACE and FOUR_CHANNELS

  Scenario: Error rate computation is zero when no crossing events
    Given the error rate computation is available
    When I compute the error rate with 0 crossing events and 100 total samples
    Then the error rate is 0.0

  Scenario: Error rate computation is correct with mixed results
    Given the error rate computation is available
    When I compute the error rate with 25 crossing events and 100 total samples
    Then the error rate is 0.25
