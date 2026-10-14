-- Test-only: maintain the default work calendar for the years the integration tests reach (no holidays, weekends off).
INSERT INTO organization_attendance.work_calendar_years (legal_entity_id, year_number)
VALUES (NULL, 2026), (NULL, 2027);
