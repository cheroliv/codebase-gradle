@epic2 @wip
Feature: EPIC 2 - Expert Anonymisation LangChain4j (MVP0)

  As a workspace developer
  I want an LLM expert to automatically anonymize configuration files
  So that GDPR compliance is guaranteed without manual intervention

  Background:
    Given the anonymization expert is initialized

  Scenario: Anonymize a YAML file with API token and password
    When I anonymize the file "codebase_config.yml"
    Then the anonymized content does not contain the string "token"
    And the anonymized content does not contain the string "password"
    And the anonymization detects at least 1 PII category
    And the confidence score is above 0.5
    And the result contains a non-empty summary

  Scenario: Anonymize a JSON file with emails
    When I anonymize the file "app_config.json"
    Then the anonymized content does not contain the symbol "@"
    And the anonymization detects the category "email"
    And the confidence score is above 0.5

  Scenario: Already clean file — no PII detected
    When I anonymize the file "safe_config.yml"
    Then the anonymized content is identical to the original content
    And the confidence score is 1.0
    And the number of replacements is 0
