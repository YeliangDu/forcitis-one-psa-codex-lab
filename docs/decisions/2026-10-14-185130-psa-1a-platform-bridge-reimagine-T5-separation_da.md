## 原话

T5 separation_date：选 (2) 撤掉该列（依据用户原话「A8 视图能提供时加；不能提供则报 psa-coordinator」）

## 落点

A8 视图无离职日期，不建 employees.separation_date；DEFERRED 登记待 A8 加列后再建并接同步；离职联动以同步离职事件触发（one-coordinator 判定）
