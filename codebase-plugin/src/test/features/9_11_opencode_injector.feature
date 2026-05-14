Feature: US-9.11 — OpencodeInjector formats CompositeContext with section headers
  As a developer
  I want to inject composite context with [REGLES_EAGER], [CONTEXTE_RAG], and [RELATIONS_GRAPHIFY] section headers
  So that the output is ready to prepend to the opencode system prompt

  Background:
    Given a pgvector container is running

  Scenario: Injected output contains all 3 section headers
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    And I compute embeddings for all chunks
    When I build a CompositeContext with the question "repository tasks database" and budget 40/30/20/10
    And I inject the composite context through OpencodeInjector
    Then the injected output contains the header "[RÈGLES_EAGER]"
    And the injected output contains the header "[CONTEXTE_RAG]"
    And the injected output contains the header "[RELATIONS_GRAPHIFY]"

  Scenario: Injected output content is not empty after injection
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    And I compute embeddings for all chunks
    When I build a CompositeContext with the question "HTTP client configuration" and budget 40/30/20/10
    And I inject the composite context through OpencodeInjector
    Then each section after its header has at least 1 line of non-empty content
