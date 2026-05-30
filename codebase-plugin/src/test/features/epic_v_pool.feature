@epic_v_pool
Feature: Pool Ollama GPT-OSS-120B — rotation, quota, failover
  As a codebase-gradle developer
  I want to validate that the 29-port pool cycles, fails over, and recovers
  So that the autonomous vibecoding loop has a reliable LLM backend

  Background:
    Given the Ollama pool is initialized with default instances

  @rotation
  Scenario: Rotation ROUND_ROBIN sur 29 ports
    Given the Ollama pool is initialized with full 29-port configuration
    When 30 consecutive LLM calls are made
    Then each pool call cycles through all 29 ports in round-robin order
    And the 30th call wraps back to port 11437

  @quota
  Scenario: Quota exceeded on port — skip to next available port
    Given a pool with 2 instances and threshold 20%
    When 4 consecutive LLM calls are made
    Then instance "a" has quota exceeded
    And instance "b" was used at least once

  @quota
  Scenario: Pool saturé — best-effort fallback
    Given a pool with 1 instance at 100% quota
    When a new LLM call is made
    Then the pool returns the instance despite quota exceeded
    And no exception is thrown

  @reset
  Scenario: Reset pool — tous les compteurs remis à zéro
    Given a pool with 2 instances and threshold 20%
    When 4 consecutive LLM calls are made
    And the pool usage is reset
    Then all instances have quota NOT exceeded
    And rotation starts again from the first instance

  @models
  Scenario: Pool full capacity — 29 ports, 5 modèles autorisés
    Given the Ollama pool is initialized with full 29-port configuration
    Then the pool has exactly 29 instances
    And the pool covers all ports from 11437 to 11465
    And the pool uses exactly 5 distinct authorized models
