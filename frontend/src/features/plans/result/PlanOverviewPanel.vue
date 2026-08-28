<template>
  <div
    v-if="traceId || goalBlueprint || phases.length || weeks.length || validationReport"
    class="plan-v2-overview"
  >
    <div class="section-header overview-header">
      <div class="section-title">学习计划 v2 规划视图</div>
      <div class="section-subtitle">
        把目标拆解、阶段安排、周节奏和校验结果一起展示出来，方便判断这份计划是否真正可执行。
      </div>
    </div>

    <div class="plan-v2-grid">
      <n-card v-if="goalBlueprint || traceId" size="small" class="overview-panel">
        <div class="sub-section-title">目标蓝图</div>
        <div class="overview-meta-row">
          <n-tag v-if="traceId" size="small" type="default">traceId：{{ traceIdShort }}</n-tag>
          <n-tag v-if="targetRole" size="small" type="info">目标方向：{{ targetRole }}</n-tag>
          <RouterLink v-if="traceId" :to="agentLogTarget" class="trace-link">查看 Agent 调用链</RouterLink>
        </div>
        <p v-if="goalBlueprint?.summary" class="helper-text overview-summary">{{ goalBlueprint.summary }}</p>

        <div v-if="topics.length" class="blueprint-topic-list">
          <div v-for="(topic, index) in topics" :key="topic.id || topic.name || index" class="blueprint-topic-item">
            <div class="blueprint-topic-order">{{ topic.order || index + 1 }}</div>
            <div class="blueprint-topic-body">
              <div class="blueprint-topic-title-row">
                <span class="blueprint-topic-title">{{ topic.name }}</span>
                <span v-if="topic.difficulty" class="blueprint-topic-meta">{{ topic.difficulty }}</span>
              </div>
              <div v-if="topic.description" class="blueprint-topic-desc">{{ topic.description }}</div>
              <div class="blueprint-topic-meta-row">
                <span v-if="topic.estimatedDays || topic.estimated_days">预计 {{ topic.estimatedDays || topic.estimated_days }} 天</span>
                <span v-if="topic.practiceType || topic.practice_type">建议方式：{{ topic.practiceType || topic.practice_type }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="milestones.length" class="blueprint-milestone-block">
          <div class="sub-section-title compact-title">阶段里程碑</div>
          <ul class="blueprint-milestone-list">
            <li v-for="(milestone, index) in milestones" :key="milestone.title || index">
              <strong>{{ milestone.title }}</strong>
              <span v-if="milestone.suggestedWeek || milestone.suggested_week">（建议第 {{ milestone.suggestedWeek || milestone.suggested_week }} 周）</span>
              ：{{ milestone.description }}
            </li>
          </ul>
        </div>
      </n-card>

      <n-card v-if="validationReport" size="small" class="overview-panel validation-panel">
        <div class="sub-section-title">计划校验结果</div>
        <div class="validation-status-row">
          <n-tag size="small" :type="isValid === false ? 'warning' : 'success'">
            {{ isValid === false ? '存在需要关注的问题' : '基础校验通过' }}
          </n-tag>
        </div>
        <div class="validation-score-grid">
          <div v-for="score in validationScores" :key="score.label" class="validation-score-item">
            <div class="validation-score-label">{{ score.label }}</div>
            <n-tag size="small" :type="validationScoreTagType(score.value)">{{ score.value ?? '--' }}</n-tag>
          </div>
        </div>

        <div v-if="issues.length" class="validation-issue-block">
          <div class="sub-section-title compact-title">问题清单</div>
          <ul class="validation-issue-list">
            <li v-for="(issue, index) in issues" :key="issue.code || index">
              <n-tag size="tiny" :type="validationIssueTagType(issue.severity)">{{ issue.severity || 'info' }}</n-tag>
              <span>
                {{ issue.message }}
                <template v-if="issue.dayIndex || issue.day_index">（关联第 {{ issue.dayIndex || issue.day_index }} 天）</template>
              </span>
            </li>
          </ul>
        </div>

        <div v-if="suggestedFixes.length" class="validation-fix-block">
          <div class="sub-section-title compact-title">优化建议</div>
          <ul class="validation-fix-list"><li v-for="(fix, index) in suggestedFixes" :key="index">{{ fix }}</li></ul>
        </div>
      </n-card>
    </div>

    <n-card v-if="phases.length" size="small" class="overview-panel">
      <div class="sub-section-title">阶段拆解</div>
      <div class="phase-grid">
        <div v-for="(phase, index) in phases" :key="phase.phaseId || phase.phase_id || index" class="phase-card">
          <div class="phase-card-title-row">
            <div class="phase-card-title">{{ phase.title }}</div>
            <n-tag size="tiny" type="info">{{ phase.weeks }} 周</n-tag>
          </div>
          <div v-if="phase.goal" class="phase-card-goal">{{ phase.goal }}</div>
          <div v-if="pickList(phase, 'focusTopics', 'focus_topics').length" class="phase-card-meta">聚焦主题：{{ pickList(phase, 'focusTopics', 'focus_topics').join(' / ') }}</div>
          <div v-if="phase.expectedOutcome || phase.expected_outcome" class="phase-card-outcome">预期产出：{{ phase.expectedOutcome || phase.expected_outcome }}</div>
        </div>
      </div>
    </n-card>

    <n-card v-if="weeks.length" size="small" class="overview-panel">
      <div class="sub-section-title">周计划节奏</div>
      <div class="week-list">
        <div v-for="(week, index) in weeks" :key="week.weekIndex || week.week_index || index" class="week-item">
          <div class="week-item-header">
            <div class="week-item-title">第 {{ week.weekIndex || week.week_index }} 周 · {{ week.theme }}</div>
            <n-tag size="tiny" type="default">{{ week.targetHours || week.target_hours }} 小时</n-tag>
          </div>
          <div v-if="pickList(week, 'focusTopics', 'focus_topics').length" class="week-item-meta">聚焦：{{ pickList(week, 'focusTopics', 'focus_topics').join(' / ') }}</div>
          <div v-if="week.milestone" class="week-item-meta">里程碑：{{ week.milestone }}</div>
          <div v-if="week.reviewStrategy || week.review_strategy" class="week-item-meta">复习策略：{{ week.reviewStrategy || week.review_strategy }}</div>
        </div>
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

// Flexible upstream plan payload; normalized access is isolated in this presentation component.
type JsonRecord = Record<string, any> // eslint-disable-line @typescript-eslint/no-explicit-any
const props = defineProps<{ plan: JsonRecord }>()

function pickValue(source: JsonRecord | null | undefined, camelKey: string, snakeKey: string) {
  return source?.[camelKey] ?? source?.[snakeKey] ?? null
}
function pickList(source: JsonRecord | null | undefined, camelKey: string, snakeKey: string) {
  const value = pickValue(source, camelKey, snakeKey)
  return Array.isArray(value) ? value : []
}

const traceId = computed(() => String(pickValue(props.plan, 'traceId', 'trace_id') || ''))
const traceIdShort = computed(() => traceId.value.length > 14 ? `${traceId.value.slice(0, 8)}...${traceId.value.slice(-4)}` : traceId.value)
const agentLogTarget = computed(() => ({ path: '/debug/agent-logs', query: { traceId: traceId.value } }))
const goalBlueprint = computed<JsonRecord | null>(() => pickValue(props.plan, 'goalBlueprint', 'goal_blueprint'))
const targetRole = computed(() => goalBlueprint.value ? pickValue(goalBlueprint.value, 'targetRole', 'target_role') : '')
const topics = computed(() => pickList(goalBlueprint.value, 'topics', 'topics'))
const milestones = computed(() => pickList(goalBlueprint.value, 'milestones', 'milestones'))
const phases = computed(() => pickList(props.plan, 'phases', 'phases'))
const weeks = computed(() => pickList(props.plan, 'weeks', 'weeks'))
const validationReport = computed<JsonRecord | null>(() => pickValue(props.plan, 'validationReport', 'validation_report'))
const isValid = computed(() => validationReport.value ? pickValue(validationReport.value, 'isValid', 'is_valid') : null)
const issues = computed(() => [
  ...pickList(validationReport.value, 'issues', 'issues'),
  ...pickList(validationReport.value, 'warnings', 'warnings'),
])
const suggestedFixes = computed(() => pickList(validationReport.value, 'suggestedFixes', 'suggested_fixes'))
const validationScores = computed(() => [
  { label: '覆盖度', value: validationReport.value ? pickValue(validationReport.value, 'coverageScore', 'coverage_score') : null },
  { label: '重复度', value: validationReport.value ? pickValue(validationReport.value, 'repetitionScore', 'repetition_score') : null },
  { label: '负载均衡', value: validationReport.value ? pickValue(validationReport.value, 'loadBalanceScore', 'load_balance_score') : null },
])

function validationScoreTagType(score: unknown) {
  if (score == null) return 'default'
  if (Number(score) >= 80) return 'success'
  if (Number(score) >= 60) return 'warning'
  return 'error'
}
function validationIssueTagType(severity: unknown) {
  if (severity === 'error') return 'error'
  if (severity === 'warning') return 'warning'
  return 'default'
}
</script>

<style scoped>
.plan-v2-overview{display:flex;flex-direction:column;gap:14px;margin-bottom:16px}.section-header{display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:4px}.overview-header{margin-bottom:0}.section-title{font-size:14px;font-weight:600;color:#111827}.section-subtitle{font-size:12px;color:#6b7280}.plan-v2-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.overview-panel{padding:8px 10px;border-radius:20px;background:linear-gradient(180deg,#fff,#fbfcfd)}.sub-section-title{margin-bottom:4px;color:#4b5563;font-size:13px;font-weight:500}.compact-title{margin-bottom:6px}.overview-meta-row{display:flex;flex-wrap:wrap;align-items:center;gap:8px;margin-bottom:8px}.trace-link{color:#2563eb;font-size:12px;text-decoration:none}.trace-link:hover{text-decoration:underline}.helper-text{color:#6b7280;font-size:12px}.overview-summary{margin:0 0 8px}.blueprint-topic-list{display:flex;flex-direction:column;gap:8px}.blueprint-topic-item{display:grid;grid-template-columns:28px 1fr;gap:8px;padding:8px 10px;border:1px solid rgba(148,163,184,.35);border-radius:8px;background:#f8fafc}.blueprint-topic-order{display:flex;width:28px;height:28px;align-items:center;justify-content:center;border-radius:999px;background:#e0f2fe;color:#0369a1;font-size:12px;font-weight:700}.blueprint-topic-body{min-width:0}.blueprint-topic-title-row{display:flex;align-items:center;justify-content:space-between;gap:8px}.blueprint-topic-title{color:#111827;font-size:13px;font-weight:600}.blueprint-topic-meta,.blueprint-topic-meta-row{color:#6b7280;font-size:12px}.blueprint-topic-desc{margin-top:4px;color:#374151;font-size:12px}.blueprint-topic-meta-row{display:flex;flex-wrap:wrap;gap:8px;margin-top:4px}.blueprint-milestone-block,.validation-issue-block,.validation-fix-block{margin-top:10px}.blueprint-milestone-list,.validation-issue-list,.validation-fix-list{margin:0;padding-left:18px;color:#374151;font-size:12px}.validation-status-row{margin-bottom:8px}.validation-score-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.validation-score-item{padding:8px 10px;border:1px solid rgba(148,163,184,.35);border-radius:8px;background:#f8fafc}.validation-score-label{margin-bottom:4px;color:#6b7280;font-size:12px}.phase-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:10px}.phase-card,.week-item{padding:10px 12px;border:1px solid rgba(148,163,184,.35);border-radius:10px;background:#f8fafc}.phase-card-title-row,.week-item-header{display:flex;align-items:flex-start;justify-content:space-between;gap:8px}.phase-card-title,.week-item-title{color:#111827;font-size:13px;font-weight:600}.phase-card-goal,.phase-card-meta,.phase-card-outcome,.week-item-meta{margin-top:4px;color:#4b5563;font-size:12px}.week-list{display:flex;flex-direction:column;gap:8px}@media(max-width:960px){.plan-v2-grid{grid-template-columns:minmax(0,1fr)}}@media(max-width:560px){.section-header{align-items:flex-start;flex-direction:column}.validation-score-grid{grid-template-columns:1fr}.overview-panel{padding:4px}}
</style>
