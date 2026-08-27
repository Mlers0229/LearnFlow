import type { DashboardLog, DashboardPlan, DashboardSummary, TrendSeries } from './types'

export const EMPTY_DASHBOARD: DashboardSummary = {
  resources: [], resourceQualityStats: [], users: [], agentLogs: [], recentPlans: [], feedbackTrend: [],
  modelConfig: null, totalPlanCount: 0, recentPlanCount7d: 0, taskStatusCounts: {},
}

export function safeTime(value?: string) {
  if (!value) return null
  const time = new Date(value).getTime()
  return Number.isNaN(time) ? null : time
}

export function logsWithin24Hours(logs: DashboardLog[], now = Date.now()) {
  const threshold = now - 24 * 60 * 60 * 1000
  return logs.filter((item) => {
    const time = safeTime(item.createdAt)
    return time != null && time >= threshold
  })
}

export function isFailedLog(log: DashboardLog) {
  const text = `${log.requestPayload || ''} ${log.responsePayload || ''}`.toLowerCase()
  return ['error', 'exception', 'failed', 'failure'].some((token) => text.includes(token))
}

export function isSlowLog(log: DashboardLog) {
  return Number(log.durationMs || 0) >= 3000
}

export function failureRate(logs: DashboardLog[]) {
  if (!logs.length) return 0
  return logs.filter(isFailedLog).length / logs.length
}

function dateLabel(value?: string) {
  const time = safeTime(value)
  if (time == null) return ''
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(time)
}

function groupPlans(plans: DashboardPlan[]) {
  const counts = new Map<string, number>()
  plans.forEach((plan) => {
    const label = dateLabel(plan.createdAt || plan.startDate)
    if (label) counts.set(label, (counts.get(label) || 0) + 1)
  })
  return [...counts.entries()].slice(-7).map(([label, value]) => ({ label, value }))
}

function groupLogs(logs: DashboardLog[]) {
  const counts = new Map<string, number>()
  logs.forEach((log) => {
    const time = safeTime(log.createdAt)
    if (time == null) return
    const label = `${new Date(time).getHours().toString().padStart(2, '0')}:00`
    counts.set(label, (counts.get(label) || 0) + 1)
  })
  return [...counts.entries()].slice(-8).map(([label, value]) => ({ label, value }))
}

export function buildTrendSeries(summary: DashboardSummary, logs24h: DashboardLog[]): TrendSeries[] {
  const feedback = summary.feedbackTrend.slice(-7).map((point) => ({
    label: dateLabel(point.date), value: Number(point.feedbackCount || 0),
  }))
  const plans = groupPlans(summary.recentPlans)
  const calls = groupLogs(logs24h)
  return [
    { key: 'feedback', title: '资源反馈', summary: `最近 ${feedback.length} 个有数据日期，共 ${feedback.reduce((sum, p) => sum + p.value, 0)} 条反馈`, color: '#c9832f', points: feedback },
    { key: 'plans', title: '计划生成样本', summary: `最近计划样本覆盖 ${plans.length} 个日期`, color: '#287d69', points: plans },
    { key: 'calls', title: 'Agent 调用样本', summary: `近 24 小时样本按小时分为 ${calls.length} 桶`, color: '#47749b', points: calls },
  ]
}

export function normalizeDashboard(data: unknown): DashboardSummary {
  const source = data && typeof data === 'object' ? data as Record<string, unknown> : {}
  return {
    resources: Array.isArray(source.resources) ? source.resources : [],
    resourceQualityStats: Array.isArray(source.resourceQualityStats) ? source.resourceQualityStats : [],
    users: Array.isArray(source.users) ? source.users : [],
    agentLogs: Array.isArray(source.agentLogs) ? source.agentLogs : [],
    recentPlans: Array.isArray(source.recentPlans) ? source.recentPlans : [],
    feedbackTrend: Array.isArray(source.feedbackTrend) ? source.feedbackTrend : [],
    modelConfig: source.modelConfig && typeof source.modelConfig === 'object' ? source.modelConfig : null,
    totalPlanCount: Number(source.totalPlanCount || 0),
    recentPlanCount7d: Number(source.recentPlanCount7d || 0),
    taskStatusCounts: source.taskStatusCounts && typeof source.taskStatusCounts === 'object' ? source.taskStatusCounts as Record<string, number> : {},
  }
}
