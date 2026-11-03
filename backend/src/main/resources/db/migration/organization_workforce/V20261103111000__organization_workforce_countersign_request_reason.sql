-- IMP-1030-FC-D（#895，one-design 裁定 #895 评论 6595446112）：撤回请求带请求人写下的理由，完成撤回时抄进命令的撤回理由。
ALTER TABLE organization_workforce.attribution_countersigns ADD COLUMN request_reason text;
ALTER TABLE organization_workforce.attribution_countersigns ADD CONSTRAINT ck_attribution_countersigns_request_reason CHECK (
    (request_kind = 'WITHDRAWAL') = (request_reason IS NOT NULL)
    AND (request_reason IS NULL OR (char_length(request_reason) BETWEEN 1 AND 500 AND btrim(request_reason) <> '')));
COMMENT ON COLUMN organization_workforce.attribution_countersigns.request_reason IS '请求理由（1 至 500 字）：撤回请求的请求人写下的理由，同一次撤回请求的各法人行文本相同，撤回完成时抄进命令的撤回理由；变更请求为空；写入后不变';
