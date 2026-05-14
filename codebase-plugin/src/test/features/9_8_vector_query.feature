Feature: US-9.8 — Simple vector query via VectorQueryService
  As a developer
  I want to query pgvector with a natural language question and get top-K relevant chunks
  So that I can retrieve semantically relevant code and documentation

  Background:
    Given a pgvector container is running

  Scenario: Query via VectorQueryService — top-K results ordered by similarity
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    And I compute embeddings for all chunks
    When I query via VectorQueryService with the phrase "repository tasks database" and topK 5
    Then the top 5 results are returned ordered by cosine similarity
    And the highest-ranked chunk is semantically related to database task retrieval

  Scenario: Query via VectorQueryService — topK parameter respected
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    And I compute embeddings for all chunks
    When I query via VectorQueryService with the phrase "HTTP client" and topK 3
    Then exactly 3 results are returned
