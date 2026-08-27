export function planStatusText(status?: string) {
  const labels: Record<string, string> = {
    active: '进行中',
    completed: '已完成',
    cancelled: '已取消',
    not_started: '未开始',
    in_progress: '进行中',
    delayed: '已延迟'
  };
  return status ? labels[status] ?? status : '未开始';
}

export function planStatusTag(status?: string) {
  const tags: Record<string, 'default' | 'info' | 'success' | 'warning'> = {
    active: 'info',
    completed: 'success',
    cancelled: 'default',
    not_started: 'default',
    in_progress: 'info',
    delayed: 'warning'
  };
  return status ? tags[status] ?? 'default' : 'default';
}

export function formatResourceDomain(domain?: string) {
  const labels: Record<string, string> = {
    java: 'Java 后端',
    python: 'Python',
    database: '数据库 / SQL',
    english: '英语',
    math: '数学',
    frontend: '前端',
    devops: 'Linux / 运维',
    general: '通用学习方法'
  };
  return labels[String(domain || '').toLowerCase()] ?? domain ?? '';
}
