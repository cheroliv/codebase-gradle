Feature: US-9.6 — Indexation YAML/JSON configs → pgvector
  As a developer
  I want YAML and JSON configuration files to be anonymized before embedding in pgvector
  So that no secrets (API keys, tokens, passwords) ever leak into the vector store

  Background:
    Given a pgvector container is running

  Scenario: Anonymize and index YAML config files
    When I tokenize each YAML config file in the dataset directory after anonymization
    And I insert the anonymized YAML configs and chunks into the pgvector database
    Then the documents table includes YAML config files
    And no YAML chunk text contains unmasked secrets

  Scenario: Anonymize and index JSON config files
    When I tokenize each JSON config file in the dataset directory after anonymization
    And I insert the anonymized JSON configs and chunks into the pgvector database
    Then the documents table includes JSON config files
    And no JSON chunk text contains unmasked secrets

  Scenario: Safe config files have no secrets to begin with
    When I tokenize all config files in the dataset directory after anonymization
    And I insert all anonymized configs and chunks into the pgvector database
    Then the vector store contains zero chunks with unmasked sensitive values
