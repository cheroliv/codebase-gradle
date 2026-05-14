@epic10 @stimulus
Feature: EPIC 10 STIMULUS — DilutionExecutor injection dans les documents racine

  Background:
    Given un repertoire de travail temporaire pour les tests STIMULUS

  @unit
  Scenario: S1 — Dilution d'une section VISION dans un document cible existant
    Given le document cible "WORKSPACE_VISION.adoc" contient deja la section "== Stimuli dilues"
    When une section VISION est diluee vers "WORKSPACE_VISION.adoc" dans la section "== Session du Test"
    Then le document cible contient la section "=== Section de test"
    And le document cible contient les metadonnees de dilution
    And une sauvegarde de securite a ete creee

  @unit
  Scenario: S2 — Dilution d'une section dans un document sans table de tracabilite
    Given le document cible "WHAT_THE_GAMES_BEEN_MISSING.adoc" ne contient pas de table de tracabilite
    When une section VISION est diluee vers "WHAT_THE_GAMES_BEEN_MISSING.adoc" dans la section "== Nouveau Test"
    Then le document cible contient la table de tracabilite "=== Stimuli dilues dans ce document"

  @unit
  Scenario: S3 — DRY RUN ne modifie pas le fichier original
    Given le document cible "WORKSPACE_ORGANIZATION.adoc" a un contenu initial
    When une section VISION est diluee en mode DRY RUN vers "WORKSPACE_ORGANIZATION.adoc"
    Then le document cible a conserve son contenu initial intact

  @unit
  Scenario: S4 — Detection de stimuli actifs dans workspace
    Given un workspace de test avec 3 fichiers .adoc stimuli et 2 documents deja dilues
    When le StimulusDetector scanne le workspace
    Then 3 stimuli actifs sont detectes
    And aucun stimulus n'est stale (tous modifies recemment)

  @unit
  Scenario: S5 — Detection de stimuli stale (> 2 jours)
    Given un workspace de test avec 1 stimulus recent et 1 stimulus vieux de 5 jours
    When le StimulusDetector detecte les stimuli stale
    Then 1 stimulus stale est detecte
    And 2 stimuli actifs sont detectes au total
