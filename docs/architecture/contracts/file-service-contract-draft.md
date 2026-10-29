# 文件服务契约草案（CS-1.10，工作名）

- 作者：one-platform-file，2026-10-21；配套 `SPLIT_PLAN.md`。
- 模块名已定：文件服务 FileService / `platform.file`（intent @225d2315），包名 `com.forcitis.one.platform.file`；其余标识符仍为工作名，待 one-glossary 登记后替换。
- 范围：ADR-0060 api 7 项 + ADR-0061 数据部分 3 项 = 10 项；ADR-0062 预览 1 项留 W2，合计 11 ≤ 12（CS-1.12）。

## 1. api（`platform.file.api`）

分两个接口，按调用方区分，方法总数计 10。

### 1.1 `StoredFiles`（业务模块与平台模块均可调）

| # | ADR 名 | 签名（工作名） | 要点 |
|---|---|---|---|
| 1 | 写入内容 | `StoreResult store(StoreRequest request)` | 同步检测（ADR-0070 决定 2）。通过 → `AVAILABLE`；引擎检出但 SHA-256 在例外清单 → `AVAILABLE`（审计「按例外放行」）；未通过 → `QUARANTINED`（返回，不抛）；加密 → 抛 `PLATFORM_FILE_ENCRYPTED_REJECTED`，只写审计、不建行；不可用 → 抛 `PLATFORM_FILE_SCAN_UNAVAILABLE`，不建行；超 100 MB → `PLATFORM_FILE_TOO_LARGE`（413） |
| 2 | 关联 | `void bind(BindRequest request)` | 分级三项（访问级别、个人信息标记、保存类别）缺一即拒；文件须 `AVAILABLE`；有上一版本的须已关联同一单据；同文件同单据重复关联幂等 |
| 3 | 封存登记 | `void hold(HoldRequest request)` | 只追加；文件须已关联到发起单据 |
| 4 | 申请下载链接 | `DownloadLink issueDownloadLink(FileId file, DocumentReference document, Duration validity)` | 由单据所属模块校验权限后调用；核对已关联、可用、未处置；`validity` 缺省 5 分钟、超上限即拒；只签正式键；`Content-Disposition: attachment` + 登记媒体类型 |
| 5 | 按单据列出 | `List<StoredFileSummary> listByDocument(DocumentReference document)` | 不含原文件名以外的个人信息；不含链接 |
| 6 | 写起算日 | `int setRetentionStart(DocumentReference anchor, LocalDate startDate)` | 作用于以 `anchor` 为起算单据的全部未处置文件，返回受影响数；新行取代旧行 |
| 7 | 写约定年限 | `int setAgreedRetention(DocumentReference anchor, Period agreed)` | 同上；合同模块调用 |
| 8 | 查到期日 | `Optional<LocalDate> expiryOf(FileId file)` | 起算日为空或类别未定年限 → empty |

### 1.2 `PlatformFileContent`（仅平台模块与迁入编排；ArchUnit 守护）

| # | ADR 名 | 签名 | 要点 |
|---|---|---|---|
| 9 | 读取内容 | `void readContent(FileId file, DocumentReference document, OutputStream sink)` | 边读边核对哈希，不符抛 `PLATFORM_FILE_HASH_MISMATCH`；调用方限 `platform..`（签章送签、一次性链接下载） |
| 10 | 迁入入口 | `ImportResult importFile(ImportRequest request)` | 只迁入编排可调；同写入流程（检测、哈希、读回）后直接写正式键并关联；拒收带原因代码，不抛（便于计数核对，ADR-0036 M6） |

### 1.3 值对象与请求

```java
record FileId(UUID value)                                 // UUIDv7
record ContentHash(String value)                          // 64 位小写十六进制，构造即校验
enum UploaderKind { USER, ONE_TIME_LINK, PLATFORM_MODULE }
record Uploader(UploaderKind kind, String id)             // USER→UserId，ONE_TIME_LINK→链接 ID，PLATFORM_MODULE→模块 id
enum DerivationKind { SIGNING, FORMAT_CONVERSION }
sealed interface Lineage permits FirstVersion, NextVersion, DerivedFrom
record NextVersion(FileId previous)
record DerivedFrom(FileId source, DerivationKind kind)

record StoreRequest(InputStream content, String originalName, String declaredMediaType,
                    Uploader uploader, Lineage lineage, IdempotencyKey idempotencyKey)
enum FileStatus { AVAILABLE, QUARANTINED, EXPIRED_UNBOUND, DISPOSED }
record StoreResult(FileId id, ContentHash hash, long size, String mediaType,
                   FileStatus status, boolean containsMacros)

enum AccessLevel { … }               // 取 ADR-0043 已定分级，不另造
enum PersonalInfoMark { NONE, PI }   // SPI 在 U-14 定前拒收（ADR-0060 决定 3）
record RetentionCategoryCode(String value)   // 由 retention_category_versions 校验存在
record BindRequest(FileId file, DocumentReference document, AccessLevel accessLevel,
                   PersonalInfoMark personalInfo, RetentionCategoryCode category,
                   DocumentReference retentionAnchor,   // 起算单据；null 表示即本单据
                   Binder boundBy)                       // USER / ONE_TIME_LINK / PLATFORM_MODULE

record HoldRequest(FileId file, DocumentReference initiator, UserId heldBy, Reason reason)
record DownloadLink(URI url, Instant expiresAt)     // url 不进日志与审计；toString 遮蔽
record StoredFileSummary(FileId id, ContentHash hash, long size, String mediaType, String originalName,
                         FileStatus status, boolean containsMacros, Uploader uploader, Instant storedAt,
                         Optional<FileId> previous, Optional<DerivedFrom> derivedFrom)
```

`originalName` 进 `stored_file_names`（`INTERNAL` + `PI`）；`declaredMediaType` 只作参考，登记值以内容判定为准，不在白名单即拒。

## 2. 前端端点（OpenAPI，`/api/v1/platform/files`）

| 端点 | 用途 | 要点 |
|---|---|---|
| `POST /api/v1/platform/files` | 上传 | 原始流（`application/octet-stream`），文件名经 `X-File-Name`（RFC 5987 编码）；必带 `Idempotency-Key`；返回 201 + `StoreResult`；未通过返回 201 + `status=QUARANTINED`（前端提示）；413 / 422（媒体类型、加密）/ 503（不可用） |
| `GET /api/v1/platform/files/{id}` | 查检测状态 | 只给本人上传、未关联的文件；其余一律 404（不区分不存在与无权） |

下载不在本模块：各业务模块在自身资源下实现 `POST …/{单据 ID}/files/{文件 ID}:download`，调 `issueDownloadLink`，响应 `{url, expiresAt}`；含宏文件响应附 `uploaderKind`、`storedAt` 供前端提示来源（ADR-0070 决定 5 第 4 条）。

## 2a. 模块内命令（不计 api，安全角色经前端）

| 命令 | 要点 |
|---|---|
| 申请读隔离内容 / 审批 / 读取 | 仅安全角色、经审批、全程审计；只经后端流式返回，不签预签名；保存期满（90 天）后内容已清，只回 SHA-256 与审计 |
| 误报判定 | 安全角色判定并写理由，状态「待复核」 |
| 误报复核 | 第二人复核，与判定人相同即拒；通过后该 SHA-256 进例外清单（只追加）；已隔离行对象键不变 |
| 延长隔离保存期 | 取证需要时单独延长并写理由 |

权限码与判定 / 复核状态取值属 ADR-0067 冻结对象，W0 冻结前与 one-data 随单元 3 定。

## 3. 事件

**W0 不发布事件**，理由：
1. 写入结论在请求内同步返回（ADR-0070），上传主体即得知；「未通过」另记 `ALERT` 与审计，不需要事件。
2. 关联、封存由所属模块调用发起，结果已知。
3. ADR-0060 迁入「不发事件」；本模块也不订阅业务事件。
4. CS-1.8：现有使用方（`organization.administration`、签章服务一段）没有订阅需求。

预留（不建）：处置执行建成时（ADR-0061 §8 后半）如有模块需知文件已处置，再按兼容新增加 `StoredFileDisposed(envelope, fileId, contentHash, method)`，届时属冻结后兼容新增（ADR-0067 决定 4 第 3 类）。

## 4. 错误码（`PlatformFileError`，中文名待 one-glossary，须全局唯一）

| 码 | ErrorKind | 拟中文名 |
|---|---|---|
| `PLATFORM_FILE_NOT_FOUND` | NOT_FOUND | 文件不存在 |
| `PLATFORM_FILE_TOO_LARGE` | INVALID（HTTP 413 由 web 层映射，待 one-architecture 定是否加 ErrorKind） | 文件超过大小上限 |
| `PLATFORM_FILE_MEDIA_TYPE_NOT_ALLOWED` | UNPROCESSABLE | 文件类型不允许 |
| `PLATFORM_FILE_ENCRYPTED_REJECTED` | UNPROCESSABLE | 加密文件不予接收 |
| `PLATFORM_FILE_SCAN_UNAVAILABLE` | UNAVAILABLE | 恶意文件检测暂不可用 |
| `PLATFORM_FILE_QUARANTINED` | CONFLICT | 文件未通过检测 |
| `PLATFORM_FILE_NOT_AVAILABLE` | CONFLICT | 文件不可用 |
| `PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT` | NOT_FOUND | 文件未关联到该单据 |
| `PLATFORM_FILE_DISPOSED` | CONFLICT | 文件已处置 |
| `PLATFORM_FILE_HASH_MISMATCH` | UNAVAILABLE | 文件哈希不符 |
| `PLATFORM_FILE_CLASSIFICATION_INCOMPLETE` | UNPROCESSABLE | 文件分级信息不全 |
| `PLATFORM_FILE_SPI_NOT_ACCEPTED` | UNPROCESSABLE | 暂不接收敏感个人信息文件 |
| `PLATFORM_FILE_PREVIOUS_VERSION_NOT_BOUND` | UNPROCESSABLE | 上一版本未关联到同一单据 |
| `PLATFORM_FILE_PREVIOUS_VERSION_HAS_SUCCESSOR` | CONFLICT | 上一版本已有后继 |
| `PLATFORM_FILE_RETENTION_CATEGORY_UNKNOWN` | UNPROCESSABLE | 保存类别不存在 |
| `PLATFORM_FILE_LINK_VALIDITY_EXCEEDED` | INVALID | 下载链接时效超过上限 |
| `PLATFORM_FILE_CONTENT_READ_FORBIDDEN` | FORBIDDEN | 无权读取文件内容 |
| `PLATFORM_FILE_QUARANTINE_READ_NOT_APPROVED` | FORBIDDEN | 隔离内容读取未获批准 |
| `PLATFORM_FILE_QUARANTINE_CONTENT_PURGED` | CONFLICT | 隔离内容已过保存期清除 |
| `PLATFORM_FILE_FALSE_POSITIVE_SAME_REVIEWER` | UNPROCESSABLE | 误报复核人与判定人相同 |

## 5. 集成层端口（`platform.integration.api`，先告知 one-architecture）

```java
interface MalwareScanner {                       // ClamAV 适配器实现；SCAN 路径，不用 INSTREAM
    ScanVerdict scan(Path stagedFile);          // CLEAN / INFECTED(signature) / ENCRYPTED；Limits.Exceeded、ERROR、连接失败、超时抛 ExternalSystemUnavailableException
    ScannerHealth health();                     // 引擎版本、库版本与库龄
}
interface BusinessFileStore {                    // 业务文件桶；禁止覆盖、写后读回核对
    void putStaging(StoredObjectKey key, Path source, ContentHash hash, long size);
    void putQuarantine(StoredObjectKey key, Path source, ContentHash hash, long size);
    void readQuarantine(StoredObjectKey key, OutputStream sink);   // 仅供隔离读取命令，经安全角色专用身份
    void promote(StoredObjectKey staging, StoredObjectKey formal, ContentHash hash, long size);  // 复制 + 读回核对
    URI presignDownload(StoredObjectKey formal, Duration validity, String fileName, String mediaType); // 非正式键即拒
    void read(StoredObjectKey formal, OutputStream sink);
}
```

对象键由文件服务按 ADR-0060 决定 3 与 ADR-0070 隔离前缀生成；端口不认识单据与文件名。

## 6. 配置项（缺失即启动失败）

单文件上限（100 MB，≤ 2 GB）、媒体类型白名单、下载链接默认与上限（5 / 15 分钟）、临时区时效（按来源，「一次性链接」≥ 链接上限 + 2 天）、检测重试次数与总时限、库龄阈值（48 h）、暂存清扫年龄（检测总时限 × 2）、隔离保存期（90 天）、请求总超时、暂存根目录与实例子目录。

## 6a. 指标

写入失败数；检测耗时 P95；按原因计数：加密拒收、病毒检出、服务不可用、超上限（网关先拒的按网关日志补计）；按例外放行数；哈希不符数；孤儿对象过期数。

## 7. 待裁

1. `PLATFORM_FILE_TOO_LARGE` 的 413：kernel `ErrorKind` 无 413，加值（kernel 改动，基础层）还是 web 层特例 —— 交 one-architecture。
2. 1.2 两个方法放独立接口以便 ArchUnit 限调用方，计数仍按 10 —— 交 one-architecture 确认 G4 计法。
3. `AccessLevel` 取值来源 —— 交 one-data（ADR-0043）。
4. `retentionAnchor` 为 ADR-0061「起算单据」的落地写法（关联时给出），ADR 未逐字规定 —— 报调度确认。

## 8. 实现回写（单元 6，2026-10-21）

- `issueDownloadLink`、`readContent` 增 `FileSubject requester`（审计用）；`HoldRequest.heldBy` 为 `FileSubject`；`BindRequest.preservationAnchor` 必填（本单据即传本单据）。
- 缺分级项由请求构造即拒（不另设 `PLATFORM_FILE_CLASSIFICATION_INCOMPLETE`）；`PLATFORM_FILE_BINDING_CONFLICT` 用于分级或保存期与已有不同。
- 关联仅限上传人或平台模块，其他主体按 `PLATFORM_FILE_NOT_FOUND`（首轮安全复审必改）。
