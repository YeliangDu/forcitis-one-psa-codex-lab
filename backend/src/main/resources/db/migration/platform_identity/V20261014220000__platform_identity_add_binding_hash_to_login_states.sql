-- 企业微信登录的 state 绑定发起请求的浏览器（M-13）：登录时必须同时带上发起时下发的绑定 cookie，只存它的哈希。
DELETE FROM platform_identity.login_states;
ALTER TABLE platform_identity.login_states ADD COLUMN binding_hash text NOT NULL;
COMMENT ON COLUMN platform_identity.login_states.binding_hash IS '发起 state 的浏览器所持绑定值的 SHA-256 十六进制；消费 state 时必须出示同一绑定值，攻击者带自己的 state 诱导他人登录时对不上';
