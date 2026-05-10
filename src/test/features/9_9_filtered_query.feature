Feature: US-9.9 — Filtered query by file type
  As a developer
  I want to filter vector queries by file type (.kt, .yml, .json, .adoc)
  So that I can retrieve only code, config, or documentation chunks

  Background:
    Given a pgvector container is running

  Scenario: Query Adoc files only — top result is documentation
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    And I compute embeddings for all chunks
    When I query via VectorQueryService filtered by file type "adoc" with phrase "governance" and topK 5
    Then the top result is from an AsciiDoc documentation file

  Scenario: Query Kotlin files only — top result is source code
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    And I compute embeddings for all chunks
    When I query via VectorQueryService filtered by file type "kt" with phrase "repository" and topK 5
    Then the top result is from a Kotlin source file

  Scenario: Query config files only — top result is YAML/JSON config
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    And I compute embeddings for all chunks
    When I query via VectorQueryService filtered by file type "yml" with phrase "configuration database" and topK 5
    Then the top result is from a YAML configuration file
