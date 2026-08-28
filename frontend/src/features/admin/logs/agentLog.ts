export type AgentLogRecord = {
  id: number
  traceId?: string | null
  agentName?: string | null
  requestPayload?: string | null
  responsePayload?: string | null
  modelName?: string | null
  durationMs?: number | null
  createdAt?: string | null
}

export type AgentLogStatus = 'SUCCESS' | 'SLOW' | 'FAILED' | 'UNKNOWN'

export type AgentLogFilters = {
  traceId: string
  taskId: string
  agent: string
  model: string
  status: '' | AgentLogStatus
  duration: '' | '1000' | '3000' | '10000'
}

export function logStatus(log: AgentLogRecord): AgentLogStatus {
  const response = String(log.responsePayload || '').trim()
  const combined = `${log.requestPayload || ''} ${response}`.toLowerCase()
  if (!response || /\b(error|exception|failed|failure|timeout)\b/.test(combined)) return 'FAILED'
  if (Number(log.durationMs || 0) >= 3000) return 'SLOW'
  if (response) return 'SUCCESS'
  return 'UNKNOWN'
}

export function filterAgentLogs(logs: AgentLogRecord[], filters: AgentLogFilters) {
  const trace = filters.traceId.trim().toLowerCase()
  const taskId = filters.taskId.trim().toLowerCase()
  const threshold = Number(filters.duration || 0)
  return [...logs]
    .filter((log) => !trace || String(log.traceId || '').toLowerCase().includes(trace))
    .filter((log) => !taskId || extractTaskId(log).toLowerCase().includes(taskId))
    .filter((log) => !filters.agent || log.agentName === filters.agent)
    .filter((log) => !filters.model || log.modelName === filters.model)
    .filter((log) => !filters.status || logStatus(log) === filters.status)
    .filter((log) => !threshold || Number(log.durationMs || 0) >= threshold)
    .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')) || Number(b.id) - Number(a.id))
}

export function summarizeAgentLogs(logs: AgentLogRecord[]) {
  const durations = logs.map((log) => Number(log.durationMs || 0)).filter((value) => value > 0).sort((a, b) => a - b)
  const p95Index = durations.length ? Math.min(durations.length - 1, Math.ceil(durations.length * 0.95) - 1) : 0
  return {
    total: logs.length,
    traces: new Set(logs.map((log) => log.traceId).filter(Boolean)).size,
    failed: logs.filter((log) => logStatus(log) === 'FAILED').length,
    slow: logs.filter((log) => logStatus(log) === 'SLOW').length,
    p95: durations.length ? durations[p95Index] : 0,
  }
}

export function payloadPreview(value?: string | null, max = 160) {
  const compact = String(value || '').replace(/\s+/g, ' ').trim()
  if (!compact) return '未记录摘要'
  return compact.length > max ? `${compact.slice(0, max)}…` : compact
}

export function prettyPayload(value?: string | null) {
  const raw = String(value || '').trim()
  if (!raw) return '未记录摘要'
  try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
}

export function extractTaskId(log: AgentLogRecord) {
  const text = `${log.requestPayload || ''} ${log.responsePayload || ''}`
  const match = /\b(?:taskId|task_id)\s*[=:"']+\s*([0-9a-f-]{16,})/i.exec(text)
  return match?.[1] || ''
}

export const statusLabel: Record<AgentLogStatus, string> = {
  SUCCESS: '正常', SLOW: '慢调用', FAILED: '异常', UNKNOWN: '未知'
}
