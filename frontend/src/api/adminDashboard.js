import { API_BASE_URL } from './config';

export async function fetchAdminDashboardSummary(params = {}) {
  const query = new URLSearchParams();
  if (params.logLimit != null) {
    query.set('logLimit', String(params.logLimit));
  }
  if (params.planLimit != null) {
    query.set('planLimit', String(params.planLimit));
  }
  if (params.trendDays != null) {
    query.set('trendDays', String(params.trendDays));
  }

  const suffix = query.toString() ? `?${query.toString()}` : '';
  const res = await fetch(`${API_BASE_URL}/api/admin/dashboard${suffix}`, {
    headers: {
      Accept: 'application/json'
    },
    cache: 'no-store'
  });

  if (!res.ok) {
    throw new Error(`获取管理端总览失败，状态码：${res.status}`);
  }

  return res.json();
}
