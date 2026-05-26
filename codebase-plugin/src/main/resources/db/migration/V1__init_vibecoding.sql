-- ============================================================================
-- V1__init_vibecoding.sql — Schéma initial pour la persistance vibecoding
-- Convention Flyway sans dépendance (versionné, sera migré vers Flyway quand
-- le seuil de 5+ migrations sera atteint — voir EPIC-V-FLYWAY dans BACKLOG.adoc)
-- ============================================================================
-- Cible : PostgreSQL avec extension pgvector (Testcontainers pgvector/pgvector:pg17)
-- Pilote : R2DBC (r2dbc-postgresql, r2dbc-pool, r2dbc-spi)
-- ============================================================================
-- Confidentialité : alignée sur les 4 cercles du Jardin Secret
--   PUBLIC      → Cercle 4 (foundry/public/ — Apache 2.0, publiable)
--   INTERNAL    → Cercle 2 (office/ — données de travail, défaut)
--   CONFIDENTIAL → Cercle 1 (configuration/ — tokens, credentials)
--   SECRET      → Cercle 0 (workspace racine — jamais persisté en DB)
-- ============================================================================

CREATE TABLE IF NOT EXISTS vibecoding_sessions (
    id              TEXT PRIMARY KEY,
    parent_session_id TEXT,
    workspace_root  TEXT NOT NULL,
    intention       TEXT NOT NULL DEFAULT '',
    dry_run         BOOLEAN NOT NULL DEFAULT false,
    max_actions     INTEGER NOT NULL DEFAULT 10,
    classification  TEXT NOT NULL DEFAULT '',
    plan_json       JSONB,
    prompt_tokens   BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    cost            DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    error           TEXT,
    finished        BOOLEAN NOT NULL DEFAULT false,
    iteration_count INTEGER NOT NULL DEFAULT 0,
    confidentiality_level TEXT NOT NULL DEFAULT 'INTERNAL'
        CHECK (confidentiality_level IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'SECRET')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS vibecoding_steps (
    id              BIGSERIAL PRIMARY KEY,
    session_id      TEXT NOT NULL REFERENCES vibecoding_sessions(id) ON DELETE CASCADE,
    step_type       TEXT NOT NULL,
    tool_name       TEXT,
    step_data       JSONB NOT NULL DEFAULT '{}',
    duration_ms     BIGINT,
    error           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_steps_session ON vibecoding_steps(session_id);
CREATE INDEX IF NOT EXISTS idx_sessions_confidentiality ON vibecoding_sessions(confidentiality_level);
CREATE INDEX IF NOT EXISTS idx_sessions_created ON vibecoding_sessions(created_at DESC);
