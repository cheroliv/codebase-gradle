@epic6 @quality_gate
Feature: EPIC 6 Quality Gate — Validation boucle qualité ONNX (checks déterministes MVP)
  As a codebase-gradle developer
  I want to validate expert LLM outputs through a quality gate pipeline
  that checks sentiment, off-topic, and PII residual
  So that only valid outputs reach downstream consumers

  Background:
    Given a QualityGate is instantiated with default deterministic checkers

  Scenario: Clean CDA output passes all quality checks
    When I evaluate CDA output "class UserRepository { fun findByName(name: String): User = entityManager.createQuery(...).singleResult }"
    Then the quality gate passes
    And all 3 checkers return PASS verdicts

  Scenario: Clean FPA output passes all quality checks
    When I evaluate FPA output "Objectif pédagogique : évaluer les compétences Qualiopi selon la taxonomie de Bloom."
    Then the quality gate passes

  Scenario: PII residual in output triggers FAIL verdict
    When I evaluate CDA output "token=ghp_1234567890abcdefghijkl class Broken {}"
    Then the quality gate fails with verdict "FAIL"
    And the failing checks contain "pii-residual"

  Scenario: Off-topic output triggers FAIL for wrong domain
    When I evaluate FPA output "@SpringBootApplication class App"
    Then the quality gate fails with verdict "FAIL"

  Scenario: Mixed issues produce FAIL and detailed assessment
    When I evaluate CDA output "La recette du gâteau. email=admin@test.com password=secret"
    Then the quality gate fails with verdict "FAIL"
    And the assessment contains at least 2 failing checks

  Scenario: Feedback message includes all failing check details
    When I evaluate CDA output "ghp_token_0123456789abcdefghijkl class Broken val x = 42"
    Then the quality gate fails
    And the feedback message contains "QUALITY_GATE"
    And the feedback message contains "pii"

  Scenario: Sentiment-negative output triggers NEEDS_FIX or FAIL
    When I evaluate CDA output "C'est nul, horrible, un désastre total, une erreur grave."
    Then the quality gate verdict is not "PASS"

  Scenario: Empty output passes all checks with max score
    When I evaluate CDA output ""
    Then the quality gate passes
    And all scores are 1.0
