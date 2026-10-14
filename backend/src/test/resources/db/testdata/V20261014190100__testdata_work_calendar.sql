-- Test-only: maintain the default work calendar for the years the integration tests reach (no holidays, weekends off).
INSERT INTO organization_attendance.work_calendar_years (id, legal_entity_id, year_number)
VALUES ('019a0000-0000-7000-8000-000000000401', NULL, 2026), ('019a0000-0000-7000-8000-000000000402', NULL, 2027);
