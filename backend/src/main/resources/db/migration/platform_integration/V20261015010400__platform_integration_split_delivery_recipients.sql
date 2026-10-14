-- 数据打分卡 K14：第一范式。收件人不再用竖线拼成一个字符串，改为文本数组。
ALTER TABLE platform_integration.outbound_deliveries ADD COLUMN recipient_codes text[];
ALTER TABLE platform_integration.outbound_deliveries ADD COLUMN skipped_recipient_codes text[];
UPDATE platform_integration.outbound_deliveries
   SET recipient_codes = array_remove(string_to_array(recipients, '|'), ''),
       skipped_recipient_codes = CASE WHEN skipped_recipients IS NULL THEN NULL ELSE array_remove(string_to_array(skipped_recipients, '|'), '') END;
ALTER TABLE platform_integration.outbound_deliveries ALTER COLUMN recipient_codes SET NOT NULL;
ALTER TABLE platform_integration.outbound_deliveries DROP COLUMN recipients;
ALTER TABLE platform_integration.outbound_deliveries DROP COLUMN skipped_recipients;
COMMENT ON COLUMN platform_integration.outbound_deliveries.recipient_codes IS '收件人：企业微信用户 ID，每人一个元素；只用精确用户投递，不用部门、标签或全员';
COMMENT ON COLUMN platform_integration.outbound_deliveries.skipped_recipient_codes IS '被跳过的收件人：测试环境白名单之外的企业微信用户 ID，每人一个元素；没有则为空';
