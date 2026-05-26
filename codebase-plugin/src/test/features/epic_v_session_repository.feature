@epic_v_session_repo
Feature: Persistance Vibecoding Session — Jardin Secret
  As a codebase-gradle developer
  I want to persist vibecoding sessions in PostgreSQL with confidentiality levels
  So that I can audit costs, analyze ontology, and filter by privacy circle

  Background:
    Given the vibecoding session repository is initialized

  @confidentiality
  Scenario: Session is created with default INTERNAL confidentiality
    When I create a vibecoding session with intention "Add dark mode toggle"
    Then the vibecoding session is created successfully
    And the vibecoding session confidentiality level is "INTERNAL"
    And the vibecoding session intention is "Add dark mode toggle"

  @confidentiality
  Scenario: Session is created with explicit CONFIDENTIAL level
    When I create a vibecoding session with intention "Secret refactoring plan" and confidentiality "CONFIDENTIAL"
    Then the vibecoding session is created successfully
    And the vibecoding session confidentiality level is "CONFIDENTIAL"

  @confidentiality
  Scenario: All four Jardin Secret levels are persisted correctly
    When I create a vibecoding session with intention "Public wiki" and confidentiality "PUBLIC"
    And I create a vibecoding session with intention "Internal doc" and confidentiality "INTERNAL"  
    And I create a vibecoding session with intention "Confidential keys" and confidentiality "CONFIDENTIAL"
    And I create a vibecoding session with intention "Secret plan" and confidentiality "SECRET"
    Then I can list vibecoding sessions by confidentiality "PUBLIC" and find exactly 1
    And I can list vibecoding sessions by confidentiality "CONFIDENTIAL" and find exactly 1
    And I can list vibecoding sessions by confidentiality "SECRET" and find exactly 1

  @update
  Scenario: Session state is updated after vibecoding run
    When I create a vibecoding session with intention "Fix typo in README"
    And I update the vibecoding session with error "Something went wrong" and finished true and iteration 3
    Then the vibecoding session error is "Something went wrong"
    And the vibecoding session is marked as finished
    And the vibecoding session iteration count is 3

  @steps
  Scenario: Steps are appended to a vibecoding session
    When I create a vibecoding session with intention "Refactor DAG"
    And I add a vibecoding step "exec_gradle" with tool "exec_shell" and duration 1500ms
    And I add a vibecoding step "llm_call" with error "Rate limit exceeded" and duration 3200ms
    Then exactly 2 vibecoding steps are linked to the session
    And the first vibecoding step has type "exec_gradle" and no error
    And the second vibecoding step has type "llm_call" and error "Rate limit exceeded"

  @cascade
  Scenario: Deleting a session cascades to its steps
    When I create a vibecoding session with intention "To be deleted"
    And I add a vibecoding step "exec_gradle" with tool "exec_shell" and duration 500ms
    And I delete the vibecoding session
    Then the vibecoding session no longer exists
    And no vibecoding steps remain for the deleted session

  @dashboard
  Scenario: Dashboard aggregates cost by confidentiality level
    When I create a vibecoding session with intention "Public A" and confidentiality "PUBLIC"
    And I create a vibecoding session with intention "Public B" and confidentiality "PUBLIC"
    And I create a vibecoding session with intention "Conf A" and confidentiality "CONFIDENTIAL"
    And I set the vibecoding session cost to 1.5 for all "PUBLIC" sessions
    And I set the vibecoding session cost to 5.0 for all "CONFIDENTIAL" sessions
    Then the vibecoding dashboard total sessions is 3
    And the vibecoding dashboard cost for confidentiality "PUBLIC" is 3.0
    And the vibecoding dashboard cost for confidentiality "CONFIDENTIAL" is 5.0
    And the vibecoding dashboard cost for confidentiality "SECRET" is 0.0

  @dashboard
  Scenario: Dashboard summary includes all fields
    When I create a vibecoding session with intention "Recent task" and confidentiality "PUBLIC"
    And I create a vibecoding session with intention "Old task" and confidentiality "INTERNAL" with createdAt 40 days ago
    Then the vibecoding dashboard summary has totalSessions 2
    And the vibecoding dashboard summary has sessionsLast7Days at least 1
    And the vibecoding dashboard summary has sessionsLast30Days at least 1
    And the vibecoding dashboard summary has a non-null lastSession

  @dashboard @empty
  Scenario: Dashboard handles empty repository gracefully
    When the vibecoding repository is empty
    Then the vibecoding dashboard total sessions is 0
    And the vibecoding dashboard total cost is 0.0
    And the vibecoding dashboard average cost per session is 0.0
    And the vibecoding dashboard most expensive session is null
    And the vibecoding dashboard last session is null
