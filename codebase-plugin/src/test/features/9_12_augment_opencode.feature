Feature: US-9.12 — augmentOpencode pipeline complet (walk→index→query→format→/tmp/opencode-context.txt)
  As a developer
  I want to run a single command that walks the workspace, indexes into pgvector, builds composite context, and outputs to /tmp/opencode-context.txt
  So that I can augment opencode with a single Gradle task

  Background:
    Given a pgvector container is running

  Scenario: Full pipeline writes opencode-context.txt with all 3 sections
    When I tokenize all dataset files into sentence-level chunks
    And I insert all documents with extracted metadata into the pgvector database
    And I compute embeddings for all chunks
    When I build a CompositeContext with the question "repository tasks database" and budget 40/30/20/10
    And I inject the composite context through OpencodeInjector
    And I write the injected output to "/tmp/opencode-context-test.txt"
    Then the file "/tmp/opencode-context-test.txt" exists
    And the file "/tmp/opencode-context-test.txt" contains "[RÈGLES_EAGER]"
    And the file "/tmp/opencode-context-test.txt" contains "[CONTEXTE_RAG]"
    And the file "/tmp/opencode-context-test.txt" contains "[RELATIONS_GRAPHIFY]"
    And the file "/tmp/opencode-context-test.txt" is larger than 300 bytes
