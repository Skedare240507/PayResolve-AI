-- V9: Add synthetic flag to cases, mark seeded demo cases, and auto-dismiss old RESOLVED DUPLICATE_EVENT rows.
-- Non-destructive migration (updates only, never deletes).

ALTER TABLE cases
    ADD COLUMN IF NOT EXISTS synthetic BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE cases
SET synthetic = TRUE
WHERE id IN ('PR-0135', 'PR-0137', 'PR-0138', 'PR-0140', 'PR-0141', 'PR-0142');

UPDATE cases
SET status = 'DISMISSED',
    summary = CASE
        WHEN summary LIKE '%auto-dismissed: absorbed duplicate or retry%' THEN summary
        ELSE summary || ' (auto-dismissed: absorbed duplicate or retry)'
    END,
    updated_at = NOW()
WHERE type = 'DUPLICATE_EVENT'
  AND status = 'RESOLVED';

