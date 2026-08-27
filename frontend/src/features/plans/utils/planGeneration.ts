import { ApiError } from '../../../shared/api/errors';
import type { PlanFormValue, PlanLevel, PlanPayload } from '../types';

export const planGenerationStages = [
  { key: 'goal', title: '理解目标与约束', description: '整理学习目标、基础水平和最终产出。', threshold: 0 },
  { key: 'schedule', title: '编排阶段与周节奏', description: '把主题拆入阶段、周计划与执行顺序。', threshold: 20 },
  { key: 'days', title: '展开每日任务', description: '生成每天的学习主题、实践任务和复习节奏。', threshold: 50 },
  { key: 'validate', title: '校验覆盖与负载', description: '检查覆盖度、重复度与每日负载是否合理。', threshold: 80 }
] as const;

export function normalizePlanLevel(value: unknown): PlanLevel {
  const normalized = String(value || '').trim().toLowerCase();
  if (normalized === 'beginner' || normalized.includes('初') || normalized.includes('零')) return 'beginner';
  if (normalized === 'advanced' || normalized.includes('进阶') || normalized.includes('高级')) return 'advanced';
  return normalized === 'intermediate' ? 'intermediate' : 'beginner';
}

export function parsePlanConstraints(value: unknown) {
  return String(value || '')
    .split(/\r?\n|[；;]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function toOptionalPlanText(value: unknown) {
  const text = String(value || '').trim();
  return text || null;
}

export function formatPlanElapsed(totalSeconds: number) {
  const safeSeconds = Math.max(0, Math.floor(Number(totalSeconds || 0)));
  const minutes = Math.floor(safeSeconds / 60);
  const seconds = safeSeconds % 60;
  return minutes ? `${minutes} 分 ${String(seconds).padStart(2, '0')} 秒` : `${seconds} 秒`;
}

export function buildPlanPayload(form: PlanFormValue): PlanPayload {
  return {
    goalText: form.goalText.trim(),
    durationWeeks: form.durationWeeks,
    hoursPerDay: form.hoursPerDay,
    level: form.level,
    targetRole: toOptionalPlanText(form.targetRole),
    preferredStyle: form.preferredStyle,
    finalDeliverable: toOptionalPlanText(form.finalDeliverable),
    constraints: parsePlanConstraints(form.constraintsText)
  };
}

export function planGenerationErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.kind === 'network') return '网络连接中断，输入草稿仍保存在本机。请恢复网络后重试，或到历史计划检查结果。';
    if (error.kind === 'rate-limit') return '计划生成请求过于频繁，请稍后再试。';
    if (error.kind === 'validation') return error.message || '计划条件不符合要求，请返回检查输入。';
    if (error.kind === 'server') return '规划服务暂时不可用。输入草稿已保留，可以稍后重试。';
  }

  const message = error instanceof Error ? error.message : '';
  if (message === 'TASK_POLL_TIMEOUT') return '任务仍在后台运行。你可以稍后在历史计划中查看结果。';
  if (message.startsWith('TASK_')) return `计划任务未完成（${message}），输入草稿已保留。`;
  return '学习计划暂时无法生成。输入草稿已保留，请稍后重试。';
}

export function newPlanIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `plan-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
