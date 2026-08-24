# 灾难恢复演练 Runbook

负责人：Incident Commander。Data、Backend、Agent、Platform 各指定执行人，另指定一名未参与实现的观察人。目标：RPO ≤ 15 分钟，RTO ≤ 60 分钟。

## 安全边界

- 破坏性演练只允许在 staging，严禁指向 production。
- 必须使用不可变应用版本、专用测试数据、批准的变更窗口和独立恢复目标。
- 不把备份、数据库内容、用户答案、Prompt、Token 或密钥复制到证据文件。
- 任一身份隔离失败、目标环境不明、恢复点不明或 production 流量出现影响时立即终止。
- 平台尚未选定时只能验证契约，不能把 Compose、本地数据库或模拟文档标记为正式演练通过。

## 演练前检查

1. 复制 `ops/recovery/drill-input.example.json` 为被 Git 忽略的 `ops/recovery/drill-input.json`。
2. 记录 release Digest/commit、运行 ID、staging 区域、开始时间和四类负责人。
3. 确认监控、告警、Trace、审计、备份元数据和备用模型/规则降级可用。
4. 建立只包含合成数据的核验清单：Flyway 版本、关键表计数、外键、所有权隔离、任务 ID 和对象存储引用。
5. 确认停止写入、切回原实例、恢复上一 Digest 和清理临时资源的操作路径。

## 必做场景

### 1. 自动备份与 PITR

验证供应商自动备份状态和保留期，在隔离实例恢复到指定时间点。执行 Flyway 校验、关键表计数、约束、所有权隔离和应用冒烟。RPO 从最后可恢复业务时刻计算，RTO 从宣布场景开始到隔离实例通过冒烟为止。

### 2. 数据库误删

在 staging 合成数据上执行批准的删除操作，立即冻结写入并选取删除前恢复点。恢复到隔离实例，验证被删除数据、删除后数据边界和幂等任务；禁止直接覆盖原库。

### 3. 数据库不可用

阻断应用访问或触发托管服务允许的受控故障，验证 readiness 关闭、连接获取有界、告警触发。完成故障转移或连接切换后，验证连接池、readiness、关键 API 和告警恢复。

### 4. 区域故障

在平台支持的隔离演练方式下停止主区域入口或副本，验证备用区域容量、镜像 Digest、Secret、对象存储、数据库恢复/副本和流量切换。未选定平台或未建立备用区域时必须记录为 `NOT_RUN`。

### 5. 模型供应商不可用

让 staging 模型桩或受控路由持续返回超时、429/5xx，验证截止时间、熔断、备用模型/规则降级和恢复。恢复后运行冻结 RAG regression，确认质量、延迟和费用边界。

### 6. 队列积压与 Worker 全重启

提交可追踪的合成异步任务，形成受控积压后重启全部 Backend Worker。验证已接受任务不丢失、不重复生成计划、过期租约被接管、取消仍有效且队列年龄恢复。

## 证据与结论

每个场景必须记录带时区的开始/恢复时间、RPO/RTO、所有必需检查和至少一个受控证据路径。证据应引用供应商事件、指标快照、告警、Trace、校验输出或测试结果，不得只写“人工确认”。

运行：

```powershell
python tools/build_recovery_report.py `
  --evidence ops/recovery/drill-input.json `
  --output-json ops/recovery/results/recovery-report.json `
  --output-markdown ops/recovery/results/recovery-report.md
```

只有报告为 `PASS` 时才可把 release candidate 的 `disasterRecoveryPassed` 设为 `true`。任一失败均建立改进项、负责人和期限，并在修复后重新完整演练受影响场景。

## 清理

删除临时故障注入和隔离恢复实例，恢复正常路由、模型配置和 Worker 数量；确认三端 readiness、队列、告警和成本恢复。归档脱敏证据，记录实际影响和改进项。
