Feature: US-9.13 — Benchmark pertinence (gate MVP0)
  As a developer
  I want to measure the quality delta between baseline LLM answers and context-augmented answers
  So that I can validate whether the composite vector improves response relevance on >70% of questions

  Scenario: Pertinence questions dataset is well-defined
    Given the pertinence questions dataset
    Then there are exactly 10 questions
    And each question has a domain in "Gouvernance, Architecture, Securite, RAG, Opencode, Anonymisation, Ecosysteme, Benchmark, Workflow, Stack"
    And each question has at least 5 expected keywords
    And each question has a minimum expected answer length of 100 characters

  Scenario: Pertinence benchmark runner is instantiable
    Then the pertinence benchmark runner can be instantiated

  Scenario: Report JSON export produces valid structured format
    Then the pertinence report JSON export produces valid format

  Scenario: Report AsciiDoc export produces valid sections
    Then the pertinence report AsciiDoc export produces valid format

  Scenario: MVP0 gate validates at 70% threshold
    Then the mvp0 gate requires improvement on more than 70 percent of questions
