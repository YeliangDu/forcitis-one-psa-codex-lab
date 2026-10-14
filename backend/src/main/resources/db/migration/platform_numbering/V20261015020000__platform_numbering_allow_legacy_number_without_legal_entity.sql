ALTER TABLE platform_numbering.legacy_numbers ALTER COLUMN legal_entity_id DROP NOT NULL;
COMMENT ON COLUMN platform_numbering.legacy_numbers.legal_entity_id IS '法人主体 ID；导入时推不出法人的原编号留空，原编号仍可检索（D-1a-152）';
