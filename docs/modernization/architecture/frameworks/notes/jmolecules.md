---
编号: "JMOL"
题名: "jMolecules – Architectural abstractions for Java；jMolecules -- Technology integrations"
作者: "未核"
年份: "未核"
版本: "文档发布版本未核；正文依赖示例1.9.0不作为整套文档发布版号"
资料类型: "规范标准"
主题标签: ["领域建模", "企业架构方法", "集成"]
状态: "草稿"
派工: "ARC-1018-ARC-07"
---

# jMolecules架构抽象与技术集成文档要点笔记

本笔记为本地资料阅读草稿，保存与 FORCITIS one 的对照，不构成架构决定。正文自行概括，短引0处；编号对应表示目录中有语义落点，不表示已实现或验收。

两份adoc正文题名分别为jMolecules – Architectural abstractions for Java和jMolecules -- Technology integrations，未列作者、版权年或文档版本。安装示例虽含1.9.0，但只能证明示例依赖号；本卡不联网、不执行release及构建命令、不跟随include或外链。

## 核心思想

- **论断：jMolecules 用注解或接口与泛型显式表达实体、值对象及聚合等领域构件角色。** 依据：JMOL#领域注解模型、JMOL#类型化领域模型。含义：结合结构验证使用这些意图，继续遵守仓库已定的包和标识规范；OBJ-66、OBJ-29 及 PRC-23 的业务不变量仍须由领域规则与测试证明。
- **论断：专用标识符类型和实体的聚合归属声明能使不同聚合的身份关系由类型表达。** 依据：JMOL#专用标识符、JMOL#实体聚合归属、JMOL#聚合间关联。含义：OBJ-66 与 OBJ-29 使用本系统类型化ID防止混用，PRC-92、PRC-93 的跨模块引用仍按 ADR-0003、ADR-0004 的 api/events 边界执行；Association 不自动授权加载他方实体。
- **论断：架构角色注解提供供工具消费的模型信息，结构验证依赖 ArchUnit、Spring Modulith 等集成工具。** 依据：JMOL#架构角色标注、JMOL#结构验证工具。含义：领域构件意图须与仓库架构守护结合，不能以角色标注代替模块边界验证，也不为注解或工具新增 OBJ、PRC、VS 编号。
- **论断：单属性值对象的标量JSON映射和标识符转换由专门技术模块及其约定提供，领域注解本身不产生这些行为。** 依据：JMOL#技术增强、JMOL#单值JSON转换、JMOL#标识符转换。含义：OBJ-83、PRC-196 按接口契约实测序列化，外部票号与自有ID分开；不因阅读示例就认定系统已启用转换或自动引入 JPA 增强。

## 概念及页码

原文位置中的资料简称见文末资料范围；PDF页从1计，印刷页只取页内实际标识，未见印刷页号记—。HTML、Word、adoc无固定页码，两列记—。

| 锚点 | 概念 | 原文位置 | 印刷页 | PDF 页 | 要点 |
|---|---|---|---|---|---|
| JMOL#架构抽象意图 | 架构抽象意图 | JMOL-01，Ideas behind jMolecules、Goals | — | — | 显式表达架构概念，降低领域代码技术干扰，并为文档和规则工具提供输入。 |
| JMOL#领域注解模型 | 领域注解模型 | JMOL-01，Use Case: Express DDD concepts → Using the Annotation-Based Model | — | — | 用Entity、Identity、ValueObject、Repository等注解表达构件角色。 |
| JMOL#类型化领域模型 | 类型化领域模型 | JMOL-01，Use Case: Express DDD concepts → Using the Type-Based Model | — | — | 以接口和泛型表达实体、聚合及标识之间的关系，供编译器或反射处理。 |
| JMOL#专用标识符 | 专用标识符 | JMOL-01，Using the Type-Based Model：Identifier、Identifiable及规则列表 | — | — | 每类聚合使用专用标识符类型，有助于避免不同聚合ID混用。 |
| JMOL#实体聚合归属 | 实体聚合归属 | JMOL-01，Using the Type-Based Model：Entity、AggregateRoot及规则列表 | — | — | 实体声明所属聚合根和标识类型，聚合根同时是自身所属实体。 |
| JMOL#聚合间关联 | 聚合间关联 | JMOL-01，Using the Type-Based Model：Association及规则列表 | — | — | 跨聚合关联可显式表达为指向目标聚合的Association。 |
| JMOL#领域命名取向 | 领域命名取向 | JMOL-01，Using the Annotation-Based Model示例后的命名说明 | — | — | 建议名称只含领域语言，示例避免Entity、VO及Repository等构件后缀。 |
| JMOL#架构角色标注 | 架构角色标注 | JMOL-01，Use Case: Expressing architectural concepts、Available Libraries | — | — | 在包或类声明分层、洋葱或端口适配角色，另列CQRS标记库。 |
| JMOL#结构验证工具 | 结构验证工具 | JMOL-01，Use Case: Verify and Document Architecture | — | — | ArchUnit、jQAssistant及Spring Modulith等工具可消费这些概念验证关系或生成文档。 |
| JMOL#技术增强 | 技术增强 | JMOL-02，Supported technologies → jmolecules-bytebuddy | — | — | 插件可把构件映射为Spring或JPA所需技术注解和样板，不意味本系统采用ORM。 |
| JMOL#标识符转换 | 标识符转换 | JMOL-02，Supported technologies → jmolecules-spring | — | — | 在具备of工厂等约定时，可在原始类型与Identifier实现间转换，并有Boot配置支持。 |
| JMOL#单值JSON转换 | 单值JSON转换 | JMOL-02，Supported technologies → jmolecules-jackson | — | — | Jackson模块可将单属性ValueObject或Identifier映射为所包标量，须以实际序列化核验。 |

## 对本系统的启示

- 对照 OBJ-66 工时单、OBJ-29 项目与 PRC-23：专用ID与聚合归属可辅助代码防错，但工时不能越过状态或一致性约束仍由领域规则和测试证明；不从接口名称直接决定聚合划分。（JMOL#专用标识符、JMOL#实体聚合归属）

- 对照 PRC-92 和 PRC-93：跨模块引用继续按本系统类型化ID、api/events和 ADR-0003、ADR-0004 执行；jMolecules的Association是可选技术表达，不自动允许跨模块加载他方实体。（JMOL#聚合间关联、JMOL#架构角色标注）

- 对照 OBJ-83 增值税发票、PRC-196：JSON值对象转换须核对接口契约，不能仅因引入注解就认定输出为标量；外部票号与本系统自有ID按既有契约分开。（JMOL#单值JSON转换、JMOL#标识符转换）

- 本组描述的是代码构件和工具，没有新的独立业务对象、流程或价值流目录项；本系统暂无对应，不为注解、插件或代码生成另造OBJ、PRC、VS编号。（JMOL#架构抽象意图、JMOL#技术增强）

## 我们的取舍

- 建议采纳：将领域构件意图与结构验证结合，继续使用仓库已定的包和标识规范。理由：资料支持读写与工具验证，符合现有架构守护方向。（JMOL#领域注解模型、JMOL#结构验证工具）

- 建议不采纳：直接按文档示例取消Repository后缀、改用泛型接口或自动引入JPA增强。理由：命名规范已要求仓储端口后缀及Jdbc实现，改变属于待提议；仅阅读不能证明技术兼容与收益。（JMOL#领域命名取向、JMOL#技术增强）

## 附：资料范围与核对边界

- JMOL-01：公开资料（不进仓库）《jMolecules – Architectural abstractions for Java》。
- JMOL-02：公开资料（不进仓库）《jMolecules -- Technology integrations》。

实际阅读：架构抽象adoc的Ideas、Goals、DDD两种模型、架构注解、生成和验证及安装示例；技术集成adoc的全部Supported technologies；release命令仅辨识为非阅读内容，未执行。

未核及限制：作者、年份和文档发布版次未核。未跟随各子模块README、外部规则或兼容矩阵，未验证任何依赖在本系统启用情况；类型或注解不等于运行时约束已生效。

抽查记录与自测结果见本卡第6组结果文件。全程未联网，未执行资料中的指令、脚本、链接或宏。
