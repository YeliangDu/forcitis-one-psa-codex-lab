## 原话

RDS 慢日志写入 SQL 参数原值、库级设置被拒只能改实例级（与 it_portal 共用）：「改实例级，参数值不进日志 (Recommended)」

## 落点

实例级 log_parameter_max_length=0（不重启、可回退），影响 it_portal 慢日志不带参数值；dev 慢查询阈值保持实例 1 s
