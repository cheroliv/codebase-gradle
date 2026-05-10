Feature: US-9.7 — Batch validation of the full indexing pipeline
  As a developer
  I want to validate that all dataset file types (.kt, .yml, .json, .adoc) can be indexed,
  embedded, and queried together in a single non-regression batch
  So that I can confirm the pipeline is stable before adding new query features

  Background:
    Given a pgvector container is running

  Scenario: Full batch — tokenize all file types, store with metadata, embed, query
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    Then the documents table has exactly 9 rows
    And the chunks table has more than 0 rows
    And each chunk has a valid foreign key to its parent document
    When I load the AllMiniLmL6V2 embedding model via ONNX Runtime
    And I compute embeddings for all chunks
    Then every chunk has a non-null embedding of dimension 384

  Scenario: Batch semantic query — cross-type relevance after full pipeline
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    And I compute embeddings for all chunks
    When I query with the phrase "HTTP client configuration"
    Then the top 5 results are returned ordered by cosine similarity
    And the top result is from a Kotlin source file

  Scenario: Batch metadata integrity — no config secrets in stored chunks
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    When I query raw chunk text for the phrase "ghp_"
    Then no chunks contain raw secrets
