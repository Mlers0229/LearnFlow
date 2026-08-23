import { describe, expect, it } from 'vitest';
import { describeAdaptation } from './adaptation';

describe('describeAdaptation', () => {
  it('explains an applied snake_case policy decision', () => {
    expect(describeAdaptation({
      applied: true,
      policy_version: 'adaptive-v1',
      target_difficulty: 'beginner',
      review_priority: 'high',
      review_interval_days: 1
    })).toContain('目标难度 beginner · 复习优先级 high · 建议间隔 1 天');
  });

  it('does not overclaim when evidence is insufficient', () => {
    expect(describeAdaptation({ applied: false, reason: 'insufficient_evidence' }))
      .toBe('掌握度证据不足，保持原计划难度与节奏。');
  });

  it('identifies the stable control cohort', () => {
    expect(describeAdaptation({ applied: false, reason: 'control', policyVersion: 'adaptive-v1' }))
      .toBe('固定策略对照 · adaptive-v1');
  });
});
