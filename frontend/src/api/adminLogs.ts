import { API_BASE_URL } from './config'
import { apiFetch } from './client'
import { apiErrorFromResponse } from '../shared/api/errors'
import type { AgentLogRecord } from '../features/admin/logs/agentLog'

export async function listAgentLogs(limit = 200): Promise<AgentLogRecord[]> {
  const query = new URLSearchParams({ limit: String(Math.min(Math.max(limit, 1), 200)) })
  const response = await apiFetch(`${API_BASE_URL}/api/agent/logs?${query}`)
  if (!response.ok) throw await apiErrorFromResponse(response, 'Agent 调用日志读取失败。')
  return response.json()
}
