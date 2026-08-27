import { describe, expect, it } from 'vitest';
import {
  buildPlanPayload,
  formatPlanElapsed,
  normalizePlanLevel,
  parsePlanConstraints
} from './planGeneration';

describe('plan generation utilities', () => {
  it('normalizes legacy learning levels', () => {
    expect(normalizePlanLevel('零基础')).toBe('beginner');
    expect(normalizePlanLevel('进阶')).toBe('advanced');
    expect(normalizePlanLevel('intermediate')).toBe('intermediate');
  });

  it('parses newline and semicolon constraints', () => {
    expect(parsePlanConstraints('仅晚上学习\n周末实践；少看长视频')).toEqual([
      '仅晚上学习',
      '周末实践',
      '少看长视频'
    ]);
  });

  it('builds a trimmed backend payload', () => {
    expect(buildPlanPayload({
      goalText: '  学习 Java  ',
      durationWeeks: 8,
      hoursPerDay: 2,
      level: 'beginner',
      targetRole: '  后端工程师 ',
      preferredStyle: 'practice_first',
      finalDeliverable: '',
      constraintsText: '每天练习'
    })).toEqual({
      goalText: '学习 Java',
      durationWeeks: 8,
      hoursPerDay: 2,
      level: 'beginner',
      targetRole: '后端工程师',
      preferredStyle: 'practice_first',
      finalDeliverable: null,
      constraints: ['每天练习']
    });
  });

  it('formats short and long elapsed durations', () => {
    expect(formatPlanElapsed(8)).toBe('8 秒');
    expect(formatPlanElapsed(66)).toBe('1 分 06 秒');
  });
});
