# one-platform-file 交接（文件服务 B 线）

- 时间：2026-10-21（恢复后更新）。
- 已合或在队：单元 1 #499、单元 2 #502（已合）、单元 4 #510（集成层端口，排队）、单元 3 #521（表结构，one-data 第七轮签，已报 one-merge 入队）。
- 单元 5（写入内容·检测闸）：PR #525 已开（分支 claude/one-platform-file-u5），含 #521、#510 提交，须二者之后入队；已报 one-coordinator 请批。容器类 IT 待 CI。
- 单元 5 复审已过：opus 首轮必改 8 条已改，sonnet 两轮复核问题已改；one-architecture 裁定已落地（PDF 按空用户口令判 R2–R6、7z 按头部 AES 判、压缩头内容加密交引擎；加密拒收审计写原文件名，用户 intent 2026-10-21-170547）。
- 待联调（OPS-02，已请 one-coordinator 转 one-ops）：content-compressed.7z 须报 Heuristics.Encrypted.7Zip，不报改走 commons-compress（报 one-architecture 与 one-coordinator 开卡）；perm-*.pdf 须扫得进、open-*.pdf 须报加密。
- #525 恢复后已合 main、修列名 transition_sequence_number（e77663a5）、补数据访问登记（0806e43e），本机容器 IT 已跑通。
- 单元 6：PR #527（after=525），opus 首轮必改已改（关联仅上传人或平台模块，加守护），sonnet 复核过；首次关联写保存期首行从单元 7 提前。
- 下一单元：6b（前端上传与状态端点、OpenAPI、identity/idempotency/scheduling 出边、正式区孤儿只读核对作业、「未关联已过期」作业；复审延后项：临时区已过期时重试关联先认正式键并映射为不可用（改集成层适配器先告知 one-architecture）、两处链接时效上限启动交叉校验、读取内容区分 sink 与存储异常、时效非正数改 4xx）、5a（隔离与误报）、7（保存期数据部分其余）。
- OPS-02 实测（one-ops-r56@3570f6ef）：clamd 1.4.6 判定与应用侧映射一致，不改 commons-compress；部署时 staging-root 定为 /data/forcitis-one/tmp/scan-staging（后端单元 ReadWritePaths 只含 logs、tmp），检测总超时 ≥60 s（clamd MaxScanTime 120 s）；EICAR、停 clamd 演练在单元 9 与 one-ops 约。
- 6b 顺手改：StoredFiles.setAgreedPreservation 注释「Written by the contract module」改为由持有约定年限的模块写入（one-coordinator 准入复审建议，不点名业务模块）。
- 6b 改名（one-glossary 评审 NAM-1021-GLO-10，claude/one-glossary-gl-10@cb20a23e9，44/46 通过）：删提议词条 pi、spi，个人信息标记取值引用 personal_information、sensitive_personal_information，枚举 PI/SPI 改 PERSONAL_INFO/SENSITIVE_PERSONAL_INFO，错误码 PLATFORM_FILE_SPI_NOT_ACCEPTED 同改；contains_macros 改 has_macros（HasMacros，api 字段 containsMacros 同改）。待定：表内代码值 'PI'（file_bindings CHECK、审计 personal=PI）是否随改——ADR-0043 用 PI 作分级代码，动手前问 one-glossary 与 one-data。改完告诉 one-glossary 转正。

## 2026-10-22 交接（会话超 8 小时，批次边界）

- 在队：#521（u3 @123fe9274）→ #525（u5 @a96ffaa5d）→ #527（u6 @e4eed965e），one-coordinator 已批，one-merge 按序入队；main 前进导致生成物过期时：合 origin/main（下游合上游分支）、build_all 连跑两次无差异后推送，报 one-merge 新 head。git 写操作一律先 cd 到工作区绝对路径。
- **下一单元：签章服务第一段 api**（先于文件 6b，挡 B-31 用印 7.3、7.4）：
  - 契约：docs/modernization/phase1a/signing/CONTRACT_DRAFT.md（@0a0f9598），one-1a-admin 已确认范围（versionAt 只抛 NO_VERSION_AT，停用由调用方按 active 判）。
  - 用语：one-glossary #564（claude/one-glossary-signing，模块标识 platform.signing、schema platform_signing，NAM-1021-GLO-16）**合入后**再落代码。
  - 步骤：api 包（4 方法、值对象、错误码）+ module-dependencies 行（platform.signing -> platform.audit, platform.identity）+ ModuleDependencyTest 禁止边 + contexts.yaml 核对；api 入库时派 opus 复审准入第 2 条（不认识业务单据）；完成后报 one-architecture 复核九条。表结构（契约 §6 四张表）交 one-data 签发后另一 PR。
- **ADR-0075 决定 2**（one-architecture 2026-10-22）：platform_file.preservation_category_versions 有状态机却无 version 列，下一张迁移卡（单元 7）补列，PreservationCategoryVersion 的转换序号取这一列。
- 文件服务其余：6b、5a、7，待办与改名见上文各条。
- 签章用语（one-glossary #564 说明）：权限码前缀 platform.signing（冒号后资源与动作按 ADR-0067 冻结，不在词表）；错误码中文名以提议文件 platform-signing.yaml 为准（统一带「印章台账」），契约草案 §2.3 拟名作废；「印章类别」统一称印章类型。#564 合入 main 后落代码。

## 2026-10-23 #642 复审后待办（6b-3 一并处理）

- PLATFORM_FILE_TOO_LARGE 词条写 413、代码为 400（ErrorKind.INVALID）：对齐（一般改为 TOO_LARGE 413，同步契约与前端）。
- 带 Idempotency-Key 的上传临时目录与扫描暂存双落盘（≤128MB×2）且无并发限额：限并发或放受配额目录。
- OpenAPI 上传成功响应改 201；generate-api.mjs 放行 Content-Disposition 补负向测试；空体上传端点测试。

## 2026-10-23 6b-3 进展（分支 claude/one-platform-file-6b3，叠在 #642 上）

- 已做：StoredFile 根类 + 「未关联已过期」作业（ExpireUnboundFiles、UnboundFileExpiryJob，ShedLock platform.file.staging.expire；时效配置 unbound-expiry-age、one-time-link-unbound-expiry-age ≥16 天、unbound-expiry-interval，部署须配）；unbuilt_roots 去 stored_files；PLATFORM_FILE_TOO_LARGE 改 413；setAgreedPreservation 注释。
- 未做：正式区孤儿只读核对（需给 BusinessFileStore 加列举方法，改 platform.integration 先告知 one-architecture，且应用 RAM 需 ListObjects）；pi/spi/HasMacros 改名（'PI' 代码值是否随改待 one-glossary、one-data）；#642 余下三项（并发限额、201、generate-api 负向）。
