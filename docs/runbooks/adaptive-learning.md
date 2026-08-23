# 掌握度自适应策略 Runbook

## 适用告警与现象

- `learnflow.adaptive.decisions` 中 `insufficient_evidence` 或固定策略比例异常上升。
- 计划、资源或练习难度与用户掌握度明显不匹配。
- `adaptive.policy.decide` Span 失败，或 V17 表访问/清理失败。
- CONTROL 与 ADAPTIVE 的行为指标没有预期差异。

## 快速检查

1. 确认 `LEARNFLOW_ADAPTIVE_ENABLED`、策略版本、实验键和分流比例。
2. 检查最近决策只包含分类摘要：

   ```sql
   SELECT variant, surface, decision_summary, count(*)
   FROM adaptive_decision
   WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
   GROUP BY variant, surface, decision_summary
   ORDER BY count(*) DESC;
   ```

3. 检查是否存在可靠画像；不要通过降低置信度门槛伪造覆盖率。
4. 沿请求 Trace 检查 `learnflow.adaptive.policy_version`、`variant`、`applied` 和 `reason`，不得出现用户 ID、知识点名称或内容正文。
5. 确认 `learnflow_agent` 对 `adaptive_policy_assignment`、`adaptive_decision` 仍无权限。

## 实验评估

- 先比较行为差异：目标难度变化率、弱项覆盖率、复习任务命中率、资源难度匹配率和练习题型分布。
- 学习效果使用后续 `learning_event` 比较复习后正确率、掌握度变化和计划完成率。
- 至少按实验键、策略版本和稳定分组分析；不得把不同版本或未复核样本混合。
- 样本量不足、分组失衡或置信区间过宽时只报告“无结论”。

## 降级与恢复

1. 紧急情况下设置 `LEARNFLOW_ADAPTIVE_ENABLED=false` 并滚动重启 Backend。
2. 若仅模型输出异常，保持 Backend 策略开启；Agent 规则路径会继续应用确定性调整。
3. 若分组或决策表异常，先停止新决策，再修复 V17 权限/索引；不要删除 `mastery_profile` 或学习事件。
4. 恢复后验证同一用户的实验分组不变、同一上下文决策键幂等、对照组不发生个性化调整。

