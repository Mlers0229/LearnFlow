import { describe, expect, it } from 'vitest';
import { filterResources, mergeResourceQuality, resourcesToCsv, type ManagedResource } from './resourceManagement';

const base = [
  { id: 1, title: 'Java 入门', domain: 'java', level: 'beginner', status: 'ACTIVE', tags: 'java,basic' },
  { id: 2, title: 'SQL 进阶', domain: 'database', level: 'advanced', status: 'PENDING', tags: 'sql' }
];

describe('resource management helpers', () => {
  it('merges quality stats and prioritizes reported resources', () => {
    const resources = mergeResourceQuality(base as never, [
      { resourceId: 1, avgRating: 4.5, feedbackCount: 2, invalidReportCount: 0 },
      { resourceId: 2, avgRating: 2, feedbackCount: 3, invalidReportCount: 2 }
    ]);
    const result = filterResources(resources, { keyword: '', domain: '', level: '', status: '', risk: '' });
    expect(result.map((item) => item.id)).toEqual([2, 1]);
    expect(result[0].invalidReportCount).toBe(2);
  });

  it('combines keyword, domain and risk filters', () => {
    const resources = mergeResourceQuality(base as never, [
      { resourceId: 2, avgRating: 2, feedbackCount: 3, invalidReportCount: 1 }
    ]);
    const result = filterResources(resources, { keyword: 'sql', domain: 'database', level: '', status: 'PENDING', risk: 'reported' });
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe(2);
  });

  it('exports UTF-8 CSV and escapes quotes', () => {
    const resource = { ...mergeResourceQuality(base as never, [])[0], title: 'Java "核心"' } as ManagedResource;
    const csv = resourcesToCsv([resource]);
    expect(csv.startsWith('\uFEFF')).toBe(true);
    expect(csv).toContain('"Java ""核心"""');
    expect(csv).toContain('"Java 后端"');
  });
});
