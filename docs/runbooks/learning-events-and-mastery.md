# 学习事件与 Mastery Profile Runbook

## 适用范围

用于排查学习事件未写入、掌握度未更新、重复计分、重算失败或画像异常。Mastery v1 是确定性业务逻辑，位于 Spring Backend，不依赖 Agent 或模型服务。

## 开关与版本

- `LEARNFLOW_MASTERY_ENABLED=true`：启用事件写入、画像查询与重算。
- `LEARNFLOW_MASTERY_ALGORITHM_VERSION=weighted-v1`：当前计算版本。
- 紧急回滚时先将 `LEARNFLOW_MASTERY_ENABLED=false` 并滚动重启 Backend；V16 是扩展迁移，无需删除表。

## 快速判断

1. 检查 Backend 的 `learnflow.mastery.events` 和 `learnflow.mastery.recomputations`，标签只能包含固定事件类型、结果和算法版本。
2. 沿请求 Trace 查找 `mastery.event.record` 与 `mastery.profile.recompute` Span；Span 不包含用户、知识点或来源 ID。
3. 使用受控只读数据库会话按匿名化用户 ID 检查：

```sql
SELECT event_type, source_type, signal_value, signal_weight, occurred_at
FROM learning_event
WHERE user_id = :user_id
ORDER BY id DESC
LIMIT 50;

SELECT k.display_name, p.mastery_score, p.confidence,
       p.effective_weight, p.sample_count, p.algorithm_version, p.calculated_at
FROM mastery_profile p
JOIN knowledge_point k ON k.id = p.knowledge_point_id
WHERE p.user_id = :user_id;
```

不得查询或复制练习题干、答案、反馈评论等业务正文到工单或聊天工具。

## 常见故障

### 业务更新成功但事件缺失

- 这是数据一致性故障；三个写入应处于同一事务，正常情况下不能部分成功。
- 检查是否关闭了 `LEARNFLOW_MASTERY_ENABLED`，再检查事务边界和数据库错误。
- 不要手工伪造事件。修复代码后使用业务来源回填脚本或受审计重算流程。

### 重复计分

- 检查 `event_key` 唯一约束是否存在。
- 检查同一 `source_type/source_id/event_type` 是否出现语义重复但事件键不同的记录。
- 练习删除应追加带 `reverses_event_id` 的 `EXERCISE_DELETED`，禁止删除原事件。

### 并发后画像滞后

- 确认 `mastery.profile.recompute` 使用 `pg_advisory_xact_lock` 串行化同一用户－知识点。
- 调用当前用户 `POST /api/mastery/recompute` 可从事件全量重放；该操作幂等。
- 若多版本并存，只比较同一 `algorithm_version`。

### 得分看似过高或过低

- 同时检查 `sample_count`、`effective_weight` 和 `confidence`，禁止只看分数。
- 资源反馈和“标记已复习”权重为 0，不应改变得分。
- 小样本置信度应明显低于 0.5；置信度硬上限为 0.95。

## 算法升级

1. 新建 ADR，定义新版本、权重、校准集和回滚阈值。
2. 修改 `LEARNFLOW_MASTERY_ALGORITHM_VERSION` 为新值，在 staging 全量重放。
3. 对比新旧版本的分数分布、置信度校准和学习效果，禁止覆盖旧版本行。
4. 验证通过后灰度切换读取版本；异常时切回旧版本并保留新结果供分析。

## 数据与权限

- Backend 可读写 `knowledge_point`、`learning_event`、`mastery_profile`。
- Agent 对三张表均无权限。
- 用户只能通过 JWT 查询或重算自己的画像；不存在接收 `userId` 的公开接口。

