-- ============================================================================
-- V2__add_session_model.sql — Ajoute la colonne model pour tracer le LLM utilisé
-- Migration requise par EPIC V-6 : updateSession persiste le model via TokenTracker
-- ============================================================================

ALTER TABLE vibecoding_sessions ADD COLUMN IF NOT EXISTS model TEXT;
