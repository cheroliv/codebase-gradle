Feature: US-9.14 --- Segregation Vision/Opinion
  As a developer
  I want the LLM to classify content sections as VISION (architecture, rules, decisions) or OPINION (preferences, speculation, brain dumps)
  So that content can be properly segregated before dilution and publication

  Scenario: Test sections dataset is well-defined
    Given the vision/opinion test sections dataset
    Then there are exactly 10 test sections
    And exactly 5 sections are expected VISION
    And exactly 5 sections are expected OPINION
    And each test section has a non-empty content
    And each test section has a valid expected classification

  Scenario: VisionOpinionClassifier is instantiable
    Then the vision/opinion classifier can be instantiated
    And the classifier has the correct system prompt with classification criteria

  Scenario: Classification JSON export produces valid format
    Then the classification report JSON export produces valid format

  Scenario: Classification AsciiDoc export produces valid format
    Then the classification report AsciiDoc export produces valid format

  Scenario: Gate validates at 80% precision threshold
    Then the US-9.14 gate requires at least 80 percent classification precision
