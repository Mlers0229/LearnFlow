export type ResourceStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE';

export interface ResourceQuality {
  resourceId: number;
  avgRating: number | null;
  feedbackCount: number;
  invalidReportCount: number;
}

export interface ManagedResource {
  id: number;
  title: string;
  url?: string | null;
  domain?: string | null;
  level?: string | null;
  durationMinutes?: number | null;
  tags?: string | null;
  status?: ResourceStatus | null;
  sourceType?: string | null;
  ingestionStatus?: string | null;
  currentIngestionId?: number | null;
  uploaderUsername?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  avgRating: number | null;
  feedbackCount: number;
  invalidReportCount: number;
}

export interface ResourceFilters {
  keyword: string;
  domain: string;
  level: string;
  status: string;
  risk: string;
}

export function normalizeStatus(status?: string | null): ResourceStatus {
  if (status === 'ACTIVE' || status === 'INACTIVE') return status;
  return 'PENDING';
}

export function mergeResourceQuality(resources: Omit<ManagedResource, 'avgRating' | 'feedbackCount' | 'invalidReportCount'>[], stats: ResourceQuality[]): ManagedResource[] {
  const qualityById = new Map(stats.map((item) => [Number(item.resourceId), item]));
  return resources.map((resource) => {
    const quality = qualityById.get(Number(resource.id));
    return {
      ...resource,
      avgRating: quality?.avgRating ?? null,
      feedbackCount: Number(quality?.feedbackCount ?? 0),
      invalidReportCount: Number(quality?.invalidReportCount ?? 0)
    };
  });
}

export function filterResources(resources: ManagedResource[], filters: ResourceFilters): ManagedResource[] {
  const keyword = filters.keyword.trim().toLowerCase();
  return resources
    .filter((resource) => {
      if (filters.domain && resource.domain !== filters.domain) return false;
      if (filters.level && resource.level !== filters.level) return false;
      if (filters.status && normalizeStatus(resource.status) !== filters.status) return false;
      if (filters.risk === 'reported' && resource.invalidReportCount <= 0) return false;
      if (!keyword) return true;
      return [resource.title, resource.url, resource.tags, resource.domain, resource.uploaderUsername]
        .some((value) => String(value ?? '').toLowerCase().includes(keyword));
    })
    .sort((left, right) => {
      const riskDifference = right.invalidReportCount - left.invalidReportCount;
      if (riskDifference) return riskDifference;
      if (normalizeStatus(left.status) !== normalizeStatus(right.status)) {
        return normalizeStatus(left.status) === 'PENDING' ? -1 : 1;
      }
      return Number(right.id) - Number(left.id);
    });
}

export function statusText(status?: string | null): string {
  if (status === 'ACTIVE') return '已上线';
  if (status === 'INACTIVE') return '已下线';
  return '待审核';
}

export function levelText(level?: string | null): string {
  if (level === 'beginner') return '零基础';
  if (level === 'intermediate') return '有一点基础';
  if (level === 'advanced') return '进阶';
  return '不限';
}

export function domainText(domain?: string | null): string {
  const labels: Record<string, string> = {
    java: 'Java 后端', python: 'Python', database: '数据库 / SQL', english: '英语',
    math: '数学', frontend: '前端', devops: 'Linux / 运维', general: '通用学习方法'
  };
  return labels[String(domain ?? '').toLowerCase()] ?? '未分类';
}

export function formatDuration(totalMinutes?: number | null): string {
  if (!totalMinutes || totalMinutes <= 0) return '—';
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return [hours ? `${hours} 小时` : '', minutes ? `${minutes} 分钟` : ''].filter(Boolean).join(' ');
}

export function splitTags(tags?: string | null): string[] {
  return String(tags ?? '').split(',').map((tag) => tag.trim()).filter(Boolean);
}

function csvCell(value: unknown): string {
  return `"${String(value ?? '').replace(/"/g, '""')}"`;
}

export function resourcesToCsv(resources: ManagedResource[]): string {
  const header = ['标题', 'URL', '领域', '水平', '时长(分钟)', '标签', '状态', '平均评分', '反馈数', '举报数'];
  const rows = resources.map((resource) => [
    resource.title, resource.url, domainText(resource.domain), levelText(resource.level), resource.durationMinutes,
    resource.tags, statusText(resource.status), resource.avgRating, resource.feedbackCount, resource.invalidReportCount
  ].map(csvCell).join(','));
  return '\uFEFF' + [header.map(csvCell).join(','), ...rows].join('\n');
}
