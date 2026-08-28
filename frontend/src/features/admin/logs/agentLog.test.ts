import { expect, it } from 'vitest'
import { extractTaskId, filterAgentLogs, logStatus, prettyPayload, summarizeAgentLogs, type AgentLogFilters, type AgentLogRecord } from './agentLog'

const logs: AgentLogRecord[] = [
  { id: 1, traceId: 'trace-a', agentName: 'PlanAgent', modelName: 'deepseek-chat', durationMs: 800, responsePayload: '{"ok":true}', createdAt: '2026-08-28T01:00:00Z' },
  { id: 2, traceId: 'trace-b', agentName: 'TutorAgent', modelName: 'deepseek-chat', durationMs: 4200, responsePayload: '{"score":80}', createdAt: '2026-08-28T02:00:00Z' },
  { id: 3, traceId: 'trace-c', agentName: 'RagAgent', durationMs: 200, requestPayload: '{"task_id":"123e4567-e89b-12d3-a456-426614174000"}', responsePayload: null, createdAt: '2026-08-28T03:00:00Z' },
]

it('derives safe operational status without exposing payload semantics', () => {
  expect(logStatus(logs[0])).toBe('SUCCESS')
  expect(logStatus(logs[1])).toBe('SLOW')
  expect(logStatus(logs[2])).toBe('FAILED')
})

it('filters and summarizes logs deterministically', () => {
  const filters: AgentLogFilters = { traceId: '', taskId: '', agent: '', model: '', status: '', duration: '3000' }
  expect(filterAgentLogs(logs, filters).map((item) => item.id)).toEqual([2])
  expect(summarizeAgentLogs(logs)).toMatchObject({ total: 3, traces: 3, failed: 1, slow: 1, p95: 4200 })
})

it('formats JSON and extracts related durable task ids', () => {
  expect(prettyPayload('{"goal":"java"}')).toContain('\n')
  expect(extractTaskId(logs[2])).toBe('123e4567-e89b-12d3-a456-426614174000')
  const filters: AgentLogFilters = { traceId: '', taskId: '123e4567', agent: '', model: '', status: '', duration: '' }
  expect(filterAgentLogs(logs, filters).map((item) => item.id)).toEqual([3])
})
