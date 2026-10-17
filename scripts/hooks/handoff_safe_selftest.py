#!/usr/bin/env python3
import importlib.util
import json
from pathlib import Path
import secrets
import string
import sys
import unittest


SPEC = importlib.util.spec_from_file_location("handoff_snapshot", Path(__file__).with_name("handoff_snapshot.py"))
snapshot = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(snapshot)


class SafeTests(unittest.TestCase):
    def hidden(self, sample):
        self.assertTrue(snapshot.safe(sample) == "[敏感内容已隐藏]", "credential rule did not redact the sample")

    def test_named_credentials(self):
        for field in ["password", "passwd", "secret", "token", "api_key", "api-key", "access_key", "authorization", "口令", "密钥", "令牌"]:
            with self.subTest(field=field):
                self.hidden(f"{field}={secrets.token_hex(4)}")

    def test_aliyun_access_key_id(self):
        self.hidden("LTAI" + "".join(secrets.choice(string.ascii_letters + string.digits) for _ in range(12)))

    def test_aws_access_key_id(self):
        self.hidden("AKIA" + "".join(secrets.choice(string.ascii_uppercase + string.digits) for _ in range(16)))

    def test_jdbc(self):
        self.hidden("jdbc:h2:mem:local")

    def test_postgres(self):
        self.hidden("postgres://localhost/local")

    def test_postgresql(self):
        self.hidden("postgresql://localhost/local")

    def test_url_credentials(self):
        for scheme in ["https", "http", "redis", "custom+db"]:
            with self.subTest(scheme=scheme):
                self.hidden(f"{scheme}://local:{secrets.token_hex(4)}@localhost/db")

    def test_json_credentials(self):
        for field in ["token", "secret", "password", "api_key", "access_key"]:
            with self.subTest(field=field):
                self.hidden(json.dumps({field: secrets.token_hex(4)}))

    def test_private_key(self):
        self.hidden("-----BEGIN " + "PRIVATE KEY-----")

    def test_github_credentials(self):
        for prefix in ["ghp_", "gho_", "ghu_", "ghs_", "ghr_", "github_pat_"]:
            with self.subTest(prefix=prefix):
                self.hidden(prefix + secrets.token_hex(4))

    def test_openai_key(self):
        self.hidden("sk-" + secrets.token_hex(4))

    def test_jwt(self):
        self.hidden("eyJ" + secrets.token_hex(4) + "." + secrets.token_hex(4))

    def test_random_string(self):
        self.hidden("Aa1" + secrets.token_urlsafe(32))

    def test_normal_titles_and_names(self):
        for sample in ["修复交接快照目录权限与消息标题解析", "claude/one-quality-handoff-hooks",
                "claude/One-Quality-Handoff-Hooks-R2-Review", "one-quality-handoff-hooks-review-round-two",
                "one-quality-handoff-hooks-r2-review-round-two", "PlainIdentifierWithoutCredentialFeatures",
                "ordinaryidentifierwithoutcredentialfeatures", "12345678-1234-1234-1234-123456789abc"]:
            with self.subTest(sample=sample):
                self.assertEqual(snapshot.safe(sample), sample)


if __name__ == "__main__":
    unittest.main(argv=[__file__, *([] if sys.argv[1:] == ["--self-test"] else sys.argv[1:])])
