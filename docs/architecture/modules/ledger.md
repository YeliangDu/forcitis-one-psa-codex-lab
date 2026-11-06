# accounting.ledger — 对外接口与「再加一个」的标准做法

范围：一页，格式同 identity、workforce 两页。上半是别的模块今天能用到的接口清单与引用统计；下半是它拥有的东西再加一个时的标准做法与文件数。核对日期 2026-11-06，origin/main；数量来自 `ls`、`git grep`，不是估计。「锁」分**今天已有**与**待建**。

职责：按法人建账（由 workforce 的 `LegalEntityRegistered` 事件触发，并可补建），目前只有应用层与领域层，没有对外接口。

## 1. 对外接口（别的模块可见的）

| 类别 | 内容 |
|---|---|
| 端口接口 | 无 |
| `api` 类型 | 共 0 个 |
| 事件（0） | 无 |
| HTTP | 0 个端点 |
| 谁在主代码里用它 | 无（别的模块的主代码没有引用它的 `api` 或 `events` 类型） |

**引用统计**（2026-11-06，origin/main；每个类名一条命令：`git grep -lw <类名> -- backend/src/main`、`git grep -lw <类名> -- backend/src/test`，再去掉模块自己目录下的文件）：

| 类别 | 个数 | 主代码有引用 | 只有测试引用 | 两边都没有 |
|---|---|---|---|---|
| `api` 类型 | 0 | 0 | 0 | 0 |
| `events` 类型 | 0 | 0 | 0 | 0 |

`api` 里没有两边都不引用的类型。
局限：按类名整词搜，同名的别处类型会让数字偏多；经 JSON 字符串、反射或 asyncapi 的使用看不到。目标形态是只暴露消费方真用的。

它用别人的（`backend/architecture/module-dependencies.txt`）：`organization.workforce`。

## 2. 它拥有的东西，再加一个的标准做法

### 2.1 加一个权限码

按 ADR-0091：在本模块的 `api` 权限声明接口加一条（码、授予方式、停用标记），`@PreAuthorize` 与使用点引用同一处，不复制常量类；要授予谁去 identity 的 `SystemRoleDeclaration` 加并加修订号。ADR-0091 迁移 P1 之前仍在 identity 的 `Permission` 里加。

**文件数**：本模块今天没有权限码；第一个码按上面做，今天落在 identity 的 `Permission` 与 `SystemRoleDeclaration` 2 个文件，目标 1 至 2 个（设计值，未实测）。

锁：`NamingSurfaceTest` G43（今天已有）；收集目录的失败检查（**待建**，ADR-0091 P1）。

### 2.2 加一个对外读端口或类型

在 `api` 加接口或 record（G2：`api` 只放接口、record、枚举），实现放 `application`；调用方在 `module-dependencies.txt` 有边（platform 层读 organization 的另要在 `platform-org-interfaces.txt` 有一行并重生成签名快照）。先看上面的引用统计，别新增没人用的。

**文件数**：今天 3 至 4 个（接口、实现、依赖登记一行、快照重生成）→ 目标不变。

锁：`ModuleDependencyTest`、`ArchitectureRulesTest` G2（今天已有）。

### 2.3 加一种账本口径（`LedgerBasis`）

在 `domain` 的 `LedgerBasis` 加值，`EnsureLedgers`/`BackfillLedgers` 按口径建账；口径是库里的值时走数据设计门。

**文件数**：`LedgerBasis` 的使用文件数今天未量；目标不变。

锁：数据设计门（`docs/data/data-design-gate.md`）与 `DdlNamingTest`（今天已有）。

## 3. 不要这样做

- 不 import 别的模块的内部类（`application`、`domain`、`infrastructure`）；只经对方的 `api`、`events` 或内核端口。
- 不在本模块复制别的模块的权限码常量。

## 4. 未核对

- 引用统计按类名整词搜，局限见第 1 节；端点数按控制器里的映射注解数出。
- 各「今天已有」的守护按类名与登记文件存在判断，断言未逐条读（identity 页第 4 节列出的几个除外）。
- 「谁在主代码里用它」按引用类型统计，不含经内核端口或事件目录的间接依赖。
- 各配方的目标文件数是 ADR-0090、0091 的设计值，未实测。
