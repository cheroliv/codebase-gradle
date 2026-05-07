Feature: US-9.2 & US-9.3 — Dataset minimal + Embeddings ONNX
  As a developer
  I want to tokenize source files into chunks, embed them with AllMiniLmL6V2 via ONNX,
  and query via pgvector cosine similarity
  So that I can retrieve semantically relevant code chunks

  Background:
    Given a pgvector container is running

  Scenario: Full pipeline — tokenize, store, embed, search
    When I tokenize each dataset file into sentence-level chunks of approximately 512 tokens
    And I insert the documents and chunks into the pgvector database
    Then the documents table has exactly 4 rows
    And the chunks table has more than 0 rows
    And each chunk has a valid foreign key to its parent document
    When I load the AllMiniLmL6V2 embedding model via ONNX Runtime
    And I compute embeddings for all chunks
    Then every chunk has a non-null embedding of dimension 384
    When I query with the phrase "find all tasks from the database"
    Then the top 5 results are returned ordered by cosine similarity
    And the highest-ranked chunk is semantically related to database task retrieval
