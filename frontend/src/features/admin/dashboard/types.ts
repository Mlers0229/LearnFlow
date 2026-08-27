import type { Component } from 'vue'

export interface DashboardResource { id?: number; title?: string; status?: string }
export interface DashboardQuality { resourceId?: number; avgRating?: number | null; feedbackCount?: number; invalidReportCount?: number }
export interface DashboardUser { id?: number; username?: string; status?: string; role?: string }
export interface DashboardLog { id?: number; traceId?: string; agentName?: string; modelName?: string; durationMs?: number; requestPayload?: string; responsePayload?: string; createdAt?: string }
export interface DashboardPlan { id?: number; title?: string; status?: string; createdAt?: string; startDate?: string; endDate?: string }
export interface FeedbackPoint { date?: string; feedbackCount?: number; invalidReportCount?: number; avgRating?: number | null }
export interface DashboardModelConfig { configured?: boolean; defaultModel?: string; catalog?: { source?: string; models?: unknown[] } }

export interface DashboardSummary {
  resources: DashboardResource[]
  resourceQualityStats: DashboardQuality[]
  users: DashboardUser[]
  agentLogs: DashboardLog[]
  recentPlans: DashboardPlan[]
  feedbackTrend: FeedbackPoint[]
  modelConfig: DashboardModelConfig | null
  totalPlanCount: number
  recentPlanCount7d: number
  taskStatusCounts: Record<string, number>
}

export interface DashboardMetric {
  key: string
  label: string
  value: string
  detail: string
  note: string
  tone: 'default' | 'good' | 'warning' | 'danger'
  icon: Component
  to?: string
  query?: Record<string, string>
}

export interface DashboardRisk {
  key: string
  title: string
  detail: string
  value: string
  severity: 'notice' | 'warning' | 'danger'
  to: string
  query?: Record<string, string>
  action: string
}

export interface TrendSeries {
  key: string
  title: string
  summary: string
  color: string
  points: Array<{ label: string; value: number }>
}
