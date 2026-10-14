-- 审批请求改按单据引用（单据类型 + 单据键）定位，必带法人；事件计数存放在聚合上。追加迁移，不改已有脚本。
-- 新增的必填列没有默认值：库里若已有行则迁移失败，而不是给旧行补一个猜测的法人或单据类型。
ALTER TABLE platform_approval.approval_requests RENAME COLUMN business_key TO document_key;
ALTER TABLE platform_approval.approval_requests ADD COLUMN document_type text NOT NULL CHECK (document_type IN ('QUOTE', 'TIMESHEET', 'JOURNAL_VOUCHER'));
ALTER TABLE platform_approval.approval_requests ADD COLUMN legal_entity_id bigint NOT NULL;
ALTER TABLE platform_approval.approval_requests ADD COLUMN event_count bigint NOT NULL DEFAULT 0;
ALTER TABLE platform_approval.approval_requests ADD CONSTRAINT ck_approval_requests_event_count CHECK (event_count >= 0);
COMMENT ON COLUMN platform_approval.approval_requests.document_key IS '单据键：由拥有该单据的业务模块给出，与单据类型一起定位单据';
COMMENT ON COLUMN platform_approval.approval_requests.document_type IS '单据类型（DocumentType 枚举名）：与流程标识一一对应';
COMMENT ON COLUMN platform_approval.approval_requests.legal_entity_id IS '法人主体（法人 ID）：由发起方传入，缺失即失败，不设默认';
COMMENT ON COLUMN platform_approval.approval_requests.event_count IS '事件计数：该聚合已记录的领域事件数，下一个事件取其加一（信封的聚合版本）';

ALTER TABLE platform_approval.start_rounds RENAME COLUMN business_key TO document_key;
COMMENT ON COLUMN platform_approval.start_rounds.document_key IS '单据键：与流程标识一起定位单据';
