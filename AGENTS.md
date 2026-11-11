# GTFI 开发约定

## 当前状态与入口
- 本仓是神秘时代4的扭曲玩法扩展：扭曲人弹射器、便携弹射器、扭曲转移器。入口为 `GTFunnyIdea`、CommonProxy/ClientProxy；服务端维护权威状态，客户端负责输入、UI与表现。
- 当前声明版本0.1.6，master编译依赖已对齐GTNH2.9.0正式版：GT5U 5.09.54.265、MUI2 2.3.94、TC4 4.2.3.5、BaublesExpanded 2.2.27。依赖取自固定GTFI正式实例并与四核心当前声明交叉核对；编译、专服业务、真人伤害与客户端GUI分别验收。
- 当前依赖Core 0.2.9，不使用本仓Mixin。已合并远端有效Core迁移，复用SolidMesh实体网格和RecipeRegistration统一配方队列，保留本仓权威业务及独立测试协议。`plan/core-migration`保留历史上下文。读取README、dependencies.gradle、build.gradle.kts和相关 `plan/sum/架构与扭曲玩法_sum.md` 即可定位，不预读全库。

## 实现边界
- `WarpService`持有来源续租、倒计时、冷却与飞行状态；扭曲读取/写回经TC4适配层，通知成功不能代替权威值写入。
- UI按真实槽位和设备UUID重新取得当前ItemStack，不能持有旧栈回写；校验所有者、目标玩家、维度、范围和过期请求。资源及扭曲转移的成功、拒绝和中断分别验收。
- 测试维度由本Mod原生实现；主动测试及ClientSmoke在 `src/testlab`，普通包只保留轻量可选bootstrap，默认关闭测试配置。原生命令要求OP2，控制者权限不能授予业务Actor；详见 `plan/测试维度指南.md`。完整testlab是同modid替换包；尚未接入四核心共享宿主，Core依赖只用于生产公共API。

## 验证与交付
- 使用仓库wrapper；`check`关联 `runCoreRulesRegression`、`runDedicatedServerAbiRegression`、`runTestlabAbiRegression`、`runTestlabPackagingRegression`，分别核规则、双源码集专服ABI和最终运行包隔离/生产条目一致性。完整构建用 `build --no-configuration-cache`，同时生成普通及reobf完整testlab包；不为纯文档改动运行构建。
- `tools/verify_delivery.py`是静态制品核验；两个 `tools/testlab` 脚本只允许显式历史RC2重放，不是正式版准备器。不用历史包或旧回执验收新候选，不为满足审计移动Tag。
- 原生测试优先复用已核定隔离实例，先确认版本与指南；不在玩家世界运行preview/build/reset。真人伤害、网络UI、专服断言分开说明，结束恢复背包、扭曲、返回位置及临时配置，不按重试复制整包。
- 保留当前分支、既有改动和独立plan仓的未提交内容。commit、Tag、推送、PR、合并、Release各按本次明确选择；文档规范化不包含升版或适配完成声明。

## 记录与技能
- 当前摘要放 `plan/sum`；本仓没有常态workflow目录，不为套流程新建流水文件。既有Wiki维护取消约定继续有效，不自动新增本地或公开Wiki同步任务。
- 使用 /gtnh-build-delivery 完成本次获准的构建与交付，按本仓Core 0.2.9依赖的事实选择附件；使用 /gtnh-docs-sync 维护实际变化的项目说明，范围限本仓。
- API问题使用 /gtnh-api-core，按当前实际消费合同核对Core依赖；MUI2使用 /gtnh-mui2，模型/材质/音频使用 /gtnh-assets。实机可使用 /gtnh-testlab 的独立项目分支，实际控制以本仓原生指南为准。
- 跨项目宗旨、技能全文和统一审阅入口见 `../开发基线校准/README.md`；Prompt只给任务摘要、关键约束与技能调用，具体步骤由技能承接。
