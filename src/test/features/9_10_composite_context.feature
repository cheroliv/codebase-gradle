Feature: US-9.10 — CompositeContextBuilder assembles EAGER + RAG + Graphify
  As a developer
  I want to build a composite context aggregating EAGER/LAZY files, RAG pgvector results, and Graphify stats
  So that I can feed opencode with a coherent multi-channel context

  Background:
    Given a pgvector container is running

  Scenario: CompositeContext built with all 3 channels populated
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    And I compute embeddings for all chunks
    When I build a CompositeContext with the question "repository tasks database" and budget 40/30/20/10
    Then the composite context has EAGER, RAG, and Graphify sections
    And the token budget totals 8000 with split 3200 EAGER, 2400 RAG, 1600 Graphify, 800 overhead

  Scenario: CompositeContext RAG section returns semantically relevant content
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    And I compute embeddings for all chunks
    When I build a CompositeContext with the question "HTTP client configuration" and budget 40/30/20/10
    Then the RAG section contains at least 3 relevance-ranked chunks
    And each chunk has a similarity score between 0.0 and 1.0
