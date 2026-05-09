Feature: US-9.5 — Indexation Kotlin metadata → pgvector
  As a developer
  I want extracted package_name and class_name metadata persisted alongside documents in pgvector
  So that I can filter semantic search results by Kotlin package

  Background:
    Given a pgvector container is running

  Scenario: Index Kotlin files with package and class metadata
    When I tokenize each dataset Kotlin file into sentence-level chunks
    And I insert the documents with extracted metadata into the pgvector database
    Then the documents table has exactly 4 rows
    And every Kotlin document has a non-null package_name
    And every Kotlin document has a non-null class_name

  Scenario: Query documents filtered by package name
    When I tokenize each dataset Kotlin file into sentence-level chunks
    And I insert the documents with extracted metadata into the pgvector database
    When I query documents by package "codebase.repository"
    Then exactly 1 document is returned with file name "TaskRepository.kt"
    And the returned document has a class_name "TaskRepository"

  Scenario: Semantic search with metadata-aware results
    When I tokenize each dataset Kotlin file into sentence-level chunks
    And I insert the documents with extracted metadata into the pgvector database
    When I load the AllMiniLmL6V2 embedding model via ONNX Runtime
    And I compute embeddings for all chunks
    When I query with the phrase "SQL tasks repository"
    Then the top result contains metadata package "codebase.repository" or class "TaskRepository"
