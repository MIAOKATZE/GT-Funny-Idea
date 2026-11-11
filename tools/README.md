# 工程工具

本目录只维护源码，运行环境、JAR、日志和报告放到明确的输出位置。Git只放行本说明及下面三份脚本，其余运行文件继续忽略。

## 入口与边界

| 工具 | 用途与前提 |
| --- | --- |
| `verify_delivery.py` | 对明确的运行JAR与实际测试候选作静态交付检查；要求源码仓干净、HEAD等于声明版本Tag，检查资源、Java8 class和精确版本差异。必须显式提供repo、jar、candidate、receipt；receipt只能写本仓build中的新JSON路径，拒绝覆盖。 |
| `testlab/prepare_instance.py` | 仅重放固定SHA的GTNH2.9.0-RC-2服务端包，独立完整解压到尚不存在的实例目录，不改其他实例。必须显式加 `--historical-rc2`；不是正式版实例准备器。 |
| `testlab/run_server.py` | 仅启动匹配GTFI/RC2/回环标记的私有实例，要求显式Java、实例和新输出目录，核对端口、单一候选和固定旧依赖；正常stop，必要时只终止自己创建的进程。必须显式加 `--historical-rc2`。 |

三份脚本的 `--help` 可直接查看。缺少历史模式开关时，两个RC2工具在访问实例、解压或启动Java前拒绝执行；无须为了文档整理创建实例。

交付审计要求HEAD等于Tag，因此不能对Tag后的文档提交直接宣称通过，也不能为迁就审计而移动Tag、改版本或重新出包。它是静态工件检查，不证明当前客户端、服务器或真人体验通过。

## 版本与输出管理

当前master源码为0.1.6，编译依赖已对齐固定GTNH2.9.0正式实例：GT5U5.09.54.265、MUI2 2.3.94、Thaumcraft4.2.3.5及BaublesExpanded2.2.27。两个RC2脚本继续仅用于历史重放，不改其固定SHA或用它们验收正式版新候选。当前原生测试使用独立testlab源码集及同modid完整替换包，默认关闭配置、OP2控制与非OP业务角色分离；没有接入四核心共享宿主。

创建实例的任务负责回收。完成时保留必要候选身份、结果与恢复证据，再按留存条件回收实例、世界、缓存与重复日志；不要把唯一源码或设计输入放入build。旧发布、合并、改可见性和重打Tag脚本不属于当前维护入口。

命令入口以 [TestCommand源码](../src/testlab/java/com/miaokatze/gtfi/testlab/TestCommand.java) 为准。本地操作与玩法说明在 `plan/测试维度指南.md`、`plan/sum/架构与扭曲玩法_sum.md`，由独立计划仓维护，不随源码仓发布。
