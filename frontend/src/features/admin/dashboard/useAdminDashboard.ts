import { computed, ref } from 'vue'
import { Bot, ClipboardList, FileCheck2, Library, UsersRound } from 'lucide-vue-next'
import { fetchAdminDashboardSummary } from '../../../api/adminDashboard'
import { buildTrendSeries, EMPTY_DASHBOARD, failureRate, isFailedLog, isSlowLog, logsWithin24Hours, normalizeDashboard } from './metrics'
import type { DashboardMetric, DashboardRisk, DashboardSummary } from './types'

const format = new Intl.NumberFormat('zh-CN')

export function useAdminDashboard() {
  const summary = ref<DashboardSummary>({ ...EMPTY_DASHBOARD })
  const loading = ref(false)
  const error = ref('')
  const updatedAt = ref<Date | null>(null)

  const logs24h = computed(() => logsWithin24Hours(summary.value.agentLogs))
  const failedLogs = computed(() => logs24h.value.filter(isFailedLog))
  const slowLogs = computed(() => logs24h.value.filter(isSlowLog))
  const avgDuration = computed(() => logs24h.value.length
    ? Math.round(logs24h.value.reduce((sum, row) => sum + Number(row.durationMs || 0), 0) / logs24h.value.length)
    : 0)

  const pendingResources = computed(() => summary.value.resources.filter((item) => (item.status || 'PENDING') === 'PENDING'))
  const activeResources = computed(() => summary.value.resources.filter((item) => item.status === 'ACTIVE'))
  const reportedResourceCount = computed(() => summary.value.resourceQualityStats.filter((item) => Number(item.invalidReportCount || 0) > 0).length)
  const activeUsers = computed(() => summary.value.users.filter((item) => (item.status || 'ACTIVE') === 'ACTIVE').length)
  const disabledUsers = computed(() => summary.value.users.filter((item) => item.status === 'DISABLED').length)
  const taskCounts = computed(() => summary.value.taskStatusCounts || {})
  const activeTasks = computed(() => ['PENDING', 'RUNNING', 'PAUSED'].reduce((sum, key) => sum + Number(taskCounts.value[key] || 0), 0))
  const failedTasks = computed(() => Number(taskCounts.value.FAILED || 0))
  const totalTasks = computed(() => Object.values(taskCounts.value).reduce((sum, value) => sum + Number(value || 0), 0))
  const modelConfigured = computed(() => Boolean(summary.value.modelConfig?.configured))
  const defaultModel = computed(() => summary.value.modelConfig?.defaultModel || '尚未配置')

  const metrics = computed<DashboardMetric[]>(() => {
    const items: DashboardMetric[] = [
    { key: 'users', label: '用户', value: format.format(summary.value.users.length), detail: `正常 ${format.format(activeUsers.value)} · 禁用 ${format.format(disabledUsers.value)}`, note: '账号实时总量', tone: disabledUsers.value ? 'warning' : 'default', icon: UsersRound, to: '/admin/users' },
    { key: 'plans', label: '学习计划', value: format.format(summary.value.totalPlanCount), detail: `近 7 天新增 ${format.format(summary.value.recentPlanCount7d)}`, note: '排除已取消计划的样本列表', tone: 'default', icon: ClipboardList },
    { key: 'tasks', label: '异步任务', value: format.format(totalTasks.value), detail: `处理中 ${format.format(activeTasks.value)} · 失败 ${format.format(failedTasks.value)}`, note: '持久任务队列状态', tone: failedTasks.value ? 'danger' : activeTasks.value ? 'warning' : 'default', icon: FileCheck2 },
    { key: 'calls', label: '模型 / Agent 调用', value: format.format(logs24h.value.length), detail: `摘要异常 ${failedLogs.value.length} · 慢调用 ${slowLogs.value.length}`, note: `24h 日志样本 · 均值 ${format.format(avgDuration.value)}ms`, tone: failedLogs.value.length ? 'danger' : slowLogs.value.length ? 'warning' : 'good', icon: Bot, to: '/admin/logs', query: { limit: '120' } },
    { key: 'failure', label: '调用异常占比', value: `${(failureRate(logs24h.value) * 100).toFixed(1)}%`, detail: `${failedLogs.value.length} / ${logs24h.value.length || 0} 条日志摘要`, note: '按 error / exception / failed 识别', tone: failedLogs.value.length ? 'danger' : 'good', icon: Bot, to: '/admin/logs', query: { mode: 'suspicious', limit: '120' } },
    { key: 'resources', label: '资源审核', value: format.format(pendingResources.value.length), detail: `上线 ${activeResources.value.length} · 被举报 ${reportedResourceCount.value}`, note: '待审核资源数量', tone: pendingResources.value.length || reportedResourceCount.value ? 'warning' : 'good', icon: Library, to: '/admin/resources', query: { status: 'PENDING' } },
    ]
    return items
  })

  const risks = computed<DashboardRisk[]>(() => {
    const items: DashboardRisk[] = []
    if (failedLogs.value.length) items.push({ key: 'failed-calls', title: 'Agent 日志出现异常摘要', detail: '按错误摘要进入日志页，再使用 Trace ID 定位调用链。', value: `${failedLogs.value.length} 条`, severity: 'danger', to: '/admin/logs', query: { mode: 'suspicious', limit: '120' }, action: '排查调用日志' })
    if (pendingResources.value.length) items.push({ key: 'pending-resources', title: '资源等待审核', detail: '待审核内容不会进入正式推荐范围。', value: `${pendingResources.value.length} 条`, severity: 'warning', to: '/admin/resources', query: { status: 'PENDING' }, action: '进入审核队列' })
    if (reportedResourceCount.value) items.push({ key: 'reported-resources', title: '资源收到无效举报', detail: '优先检查举报次数和资源当前上线状态。', value: `${reportedResourceCount.value} 条`, severity: 'warning', to: '/admin/resources', query: { risk: 'reported' }, action: '查看高风险资源' })
    if (!modelConfigured.value) items.push({ key: 'model', title: '默认模型尚未完成配置', detail: '模型目录、凭据或默认模型仍有缺项。', value: '待配置', severity: 'notice', to: '/admin/models', action: '检查模型配置' })
    return items
  })

  const trends = computed(() => buildTrendSeries(summary.value, logs24h.value))
  const latestPlans = computed(() => summary.value.recentPlans.slice(0, 5))

  async function load() {
    loading.value = true
    error.value = ''
    try {
      summary.value = normalizeDashboard(await fetchAdminDashboardSummary({ logLimit: 120, planLimit: 50, trendDays: 7 }))
      updatedAt.value = new Date()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '管理总览加载失败，请稍后重试。'
    } finally {
      loading.value = false
    }
  }

  return { summary, loading, error, updatedAt, metrics, risks, trends, latestPlans, taskCounts, modelConfigured, defaultModel, load }
}
