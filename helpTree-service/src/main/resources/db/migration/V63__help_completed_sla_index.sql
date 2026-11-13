-- Keep the completed-help SLA sweep bounded to live rows ordered by completed_at.
CREATE INDEX IF NOT EXISTS idx_helps_completed_sla
    ON helps (completed_at)
    WHERE status = 'COMPLETED'
      AND (deleted = false OR deleted IS NULL);
