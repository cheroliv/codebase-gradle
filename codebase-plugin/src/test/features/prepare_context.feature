Feature: CollectFromCodebase — Pipeline Contexte Augmenté per Borough
  As a developer
  I want to validate that collectFromCodebase walk→index→buildScoped→inject produces correct context per borough
  So that the pipeline is reliable before deploying to all N2 boroughs

  Background:
    Given a pgvector container is running

  Scenario: CollectFromCodebase scoped — index fixtures + buildScoped + verify output file
    When I tokenize each dataset file into sentence-level chunks of approximately 512 tokens
    And I insert the documents and chunks into the pgvector database
    And I compute embeddings for all chunks
    When I run collectFromCodebase scoped to "test-borough" with question "repository tasks database"
    Then the output file "build/context/test-borough.context.txt" exists
    And the output file "build/context/test-borough.context.txt" contains "[RÈGLES_EAGER]"
    And the output file size is at least 200 bytes

  Scenario: CollectFromCodebase scoped — verify EAGER section contains INDEX content
    When I tokenize each dataset file into sentence-level chunks of approximately 512 tokens
    And I insert the documents and chunks into the pgvector database
    And I compute embeddings for all chunks
    When I run collectFromCodebase scoped to "test-borough-eager" with question "HTTP client configuration"
    Then the output file "build/context/test-borough-eager.context.txt" contains "[RÈGLES_EAGER]"
    And the output file "build/context/test-borough-eager.context.txt" contains "[CONTEXTE_RAG]"

  Scenario: CollectFromCodebase scoped — verify RAG section contains relevant results
    When I tokenize each dataset file into sentence-level chunks of approximately 512 tokens
    And I insert the documents and chunks into the pgvector database
    And I compute embeddings for all chunks
    When I run collectFromCodebase scoped to "test-borough-rag" with question "HTTP client configuration"
    Then the output file "build/context/test-borough-rag.context.txt" contains "[CONTEXTE_RAG]"
    And the RAG section in output file contains at least 1 similarity-scored chunk

  Scenario: CollectFromCodebase scoped — verify Graphify section present
    When I tokenize each dataset file into sentence-level chunks of approximately 512 tokens
    And I insert the documents and chunks into the pgvector database
    And I compute embeddings for all chunks
    When I run collectFromCodebase scoped to "test-borough-graphify" with question "repository tasks database"
    Then the output file "build/context/test-borough-graphify.context.txt" contains "[RELATIONS_GRAPHIFY]"
