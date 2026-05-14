Feature: US-9.3 & US-9.4 — WorkspaceWalker + Indexation adoc
  As a developer
  I want a recursive workspace walker that discovers all relevant files
  and indexes both Kotlin and AsciiDoc content into pgvector
  So that the RAG pipeline can serve cross-language code + documentation search

  Background:
    Given a pgvector container is running

  Scenario: WorkspaceWalker discovers all source files skipping build artifacts
    When I walk the datasets directory with WorkspaceWalker
    Then at least 6 files are discovered
    And all discovered files have valid extension metadata
    And no files from build, .git, .gradle, or node_modules are included

  Scenario: Index both Kotlin and AsciiDoc files into pgvector
    When I tokenize all dataset files into sentence-level chunks
    And I insert the documents and chunks into the pgvector database
    Then the documents table has exactly 6 rows
    And the chunks table has more than 0 rows
    When I compute embeddings for all chunks
    Then every chunk has a non-null embedding of dimension 384
    When I query with the phrase "gouvernance agent obligatoire"
    Then the top result is from an AsciiDoc documentation file
    When I query with the phrase "repository JDBC SQL tasks"
    Then the top result is from a Kotlin source file
