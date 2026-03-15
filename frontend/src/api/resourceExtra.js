import { API_BASE_URL } from './config';

/**
 * 获取某资源的最近反馈明细
 * @param {number|string} resourceId
 * @param {number} limit
 */
export async function getResourceFeedbacks(resourceId, limit = 20) {
  const res = await fetch(
    `${API_BASE_URL}/api/resources/${resourceId}/feedbacks?limit=${encodeURIComponent(limit)}`
  );
  if (!res.ok) {
    throw new Error(`获取资源反馈失败，状态码：${res.status}`);
  }
  return res.json();
}

/**
 * 获取按天聚合的反馈趋势
 * @param {number} days
 */
export async function getFeedbackTrend(days = 30) {
  const res = await fetch(
    `${API_BASE_URL}/api/resources/feedback/trend?days=${encodeURIComponent(days)}`
  );
  if (!res.ok) {
    throw new Error(`获取反馈趋势失败，状态码：${res.status}`);
  }
  return res.json();
}


