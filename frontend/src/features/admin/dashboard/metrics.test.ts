import { describe, expect, it } from 'vitest'
import { buildTrendSeries, failureRate, isFailedLog, isSlowLog, logsWithin24Hours, normalizeDashboard } from './metrics'

describe('admin dashboard metrics', () => {
  it('normalizes missing collections and preserves backend counters', () => {
    const summary = normalizeDashboard({ totalPlanCount: 42, recentPlanCount7d: 8, taskStatusCounts: { FAILED: 2 } })
    expect(summary.resources).toEqual([])
    expect(summary.totalPlanCount).toBe(42)
    expect(summary.taskStatusCounts.FAILED).toBe(2)
  })

  it('separates error summaries from slow calls', () => {
    const slow = { durationMs: 4500, responsePayload: '{"status":"ok"}' }
    const failed = { durationMs: 200, responsePayload: '{"error":"provider unavailable"}' }
    expect(isSlowLog(slow)).toBe(true)
    expect(isFailedLog(slow)).toBe(false)
    expect(isFailedLog(failed)).toBe(true)
    expect(failureRate([slow, failed])).toBe(0.5)
  })

  it('limits the call sample to the previous 24 hours', () => {
    const now = new Date('2026-08-27T08:00:00Z').getTime()
    const logs = logsWithin24Hours([
      { createdAt: '2026-08-27T07:00:00Z' },
      { createdAt: '2026-08-25T07:00:00Z' },
    ], now)
    expect(logs).toHaveLength(1)
  })

  it('builds accessible trend summaries from real samples', () => {
    const summary = normalizeDashboard({
      feedbackTrend: [{ date: '2026-08-26', feedbackCount: 3 }],
      recentPlans: [{ createdAt: '2026-08-26T09:00:00Z' }],
    })
    const series = buildTrendSeries(summary, [{ createdAt: '2026-08-26T09:20:00Z' }])
    expect(series.map((item) => item.points.length)).toEqual([1, 1, 1])
    expect(series[0].summary).toContain('3 条反馈')
  })
})
