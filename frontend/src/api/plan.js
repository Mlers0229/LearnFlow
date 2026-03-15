import { API_BASE_URL } from './config';

function toCamelKey(key) {
  return key.replace(/_([a-z])/g, (_, char) => char.toUpperCase());
}

function camelizeKeys(value) {
  if (Array.isArray(value)) {
    return value.map(camelizeKeys);
  }

  if (value && typeof value === 'object') {
    return Object.entries(value).reduce((acc, [key, currentValue]) => {
      acc[toCamelKey(key)] = camelizeKeys(currentValue);
      return acc;
    }, {});
  }

  return value;
}

function normalizePlanResponse(payload) {
  if (!payload) return payload;
  return camelizeKeys(payload);
}

/**
 * 调用后端 /api/plan 接口，生成学习计划。
 * @param {Object} payload - { goalText, durationWeeks, hoursPerDay, level, userId? }
 */
export async function generatePlan(payload) {
  const res = await fetch(`${API_BASE_URL}/api/plan`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    throw new Error(`后端返回错误状态码：${res.status}`);
  }

  return normalizePlanResponse(await res.json());
}

/**
 * 获取最近生成的学习计划列表。
 * 对应后端 GET /api/plan/recent?limit=5
 * @param {number} limit - 返回的计划数量，默认 5
 * @param {number} userId - 当前登录用户 ID
 */
export async function getRecentPlans(limit = 5, userId) {
  const query = new URLSearchParams();
  query.append('limit', String(limit));
  if (userId != null) {
    query.append('userId', String(userId));
  }
  const res = await fetch(
    `${API_BASE_URL}/api/plan/recent?${query.toString()}`
  );

  if (!res.ok) {
    throw new Error(`获取历史计划失败，状态码：${res.status}`);
  }

  return camelizeKeys(await res.json());
}

/**
 * 根据 planId 获取完整的学习计划详情。
 * 对应后端 GET /api/plan/{id}?userId=...
 * @param {number|string} id - 计划 ID
 * @param {number} userId - 当前登录用户 ID
 */
export async function getPlanById(id, userId) {
  const query = new URLSearchParams();
  if (userId != null) {
    query.append('userId', String(userId));
  }
  const url =
    query.toString().length > 0
      ? `${API_BASE_URL}/api/plan/${id}?${query.toString()}`
      : `${API_BASE_URL}/api/plan/${id}`;
  const res = await fetch(url);

  if (!res.ok) {
    throw new Error(`获取计划详情失败，状态码：${res.status}`);
  }

  return normalizePlanResponse(await res.json());
}

/**
 * 获取某一天的推荐学习资源。
 * 对应后端 GET /api/plan/day/{dayId}/resources
 * @param {number|string} dayId - 学习计划中某一天的 ID
 * @param {number} [userId] - 当前登录用户 ID，用于回填本人反馈
 */
export async function getResourcesByDay(dayId, userId) {
  const query = new URLSearchParams();
  if (userId != null) {
    query.append('userId', String(userId));
  }
  const url = query.toString()
    ? `${API_BASE_URL}/api/plan/day/${dayId}/resources?${query.toString()}`
    : `${API_BASE_URL}/api/plan/day/${dayId}/resources`;
  const res = await fetch(url);

  if (!res.ok) {
    throw new Error(`获取推荐资源失败，状态码：${res.status}`);
  }

  return normalizePlanResponse(await res.json());
}

/**
 * 获取整份计划的推荐学习资源。
 * 对应后端 GET /api/plan/{planId}/resources
 * @param {number|string} planId - 计划 ID
 * @param {number} [userId] - 当前登录用户 ID，用于回填本人反馈
 */
export async function getResourcesByPlan(planId, userId) {
  const query = new URLSearchParams();
  if (userId != null) {
    query.append('userId', String(userId));
  }
  const url = query.toString()
    ? `${API_BASE_URL}/api/plan/${planId}/resources?${query.toString()}`
    : `${API_BASE_URL}/api/plan/${planId}/resources`;
  const res = await fetch(url);

  if (!res.ok) {
    throw new Error(`获取计划推荐资源失败，状态码：${res.status}`);
  }

  return res.json();
}

/**
 * 更新某一天的学习状态（打卡）。
 * 对应后端 PATCH /api/plan/day/{dayId}/status
 * @param {number|string} dayId - 学习计划中某一天的 ID
 * @param {'not_started'|'in_progress'|'completed'|'delayed'} status - 新状态
 */
export async function updateDayStatus(dayId, status) {
  const res = await fetch(`${API_BASE_URL}/api/plan/day/${dayId}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ status })
  });

  if (!res.ok) {
    throw new Error(`更新学习状态失败，状态码：${res.status}`);
  }
}

/**
 * 从某一天开始顺延并重排后续学习任务。
 * 对应后端 POST /api/plan/{id}/replan
 * @param {number|string} planId - 计划 ID
 * @param {{ userId: number, triggerDayId: number|string, delayDays?: number, reason?: string }} payload
 */
export async function replanPlan(planId, payload) {
  const res = await fetch(`${API_BASE_URL}/api/plan/${planId}/replan`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    throw new Error(`重规划学习计划失败，状态码：${res.status}`);
  }

  return res.json();
}

/**
 * 获取某个学习计划的整体完成情况。
 * 对应后端 GET /api/plan/{id}/progress
 * @param {number|string} planId - 计划 ID
 */
export async function getPlanProgress(planId) {
  const res = await fetch(`${API_BASE_URL}/api/plan/${planId}/progress`);

  if (!res.ok) {
    throw new Error(`获取计划完成率失败，状态码：${res.status}`);
  }

  return res.json();
}

/**
 * 细化某一天的学习任务。
 * 对应后端 POST /api/plan/day/{dayId}/refine
 * @param {number|string} dayId - 学习计划中某一天的 ID
 * @returns {Promise<{id:number,date:string,title:string,tasks:string[],status:string}>}
 */
export async function refineDay(dayId) {
  const res = await fetch(`${API_BASE_URL}/api/plan/day/${dayId}/refine`, {
    method: 'POST'
  });

  if (!res.ok) {
    throw new Error(`细化当日任务失败，状态码：${res.status}`);
  }

  return res.json();
}

/**
 * 为某一天生成练习题。
 * 对应后端 GET /api/plan/day/{dayId}/exercises
 * @param {number|string} dayId - 学习计划中某一天的 ID
 * @returns {Promise<Array<{question:string,answer:string,explanation?:string}>>}
 */
export async function getExercisesByDay(dayId) {
  const res = await fetch(`${API_BASE_URL}/api/plan/day/${dayId}/exercises`);

  if (!res.ok) {
    throw new Error(`获取练习题失败，状态码：${res.status}`);
  }

  return res.json();
}

/**
 * 评测某一天的一道练习题作答。
 * 对应后端 POST /api/plan/day/{dayId}/exercise-evaluate
 * @param {number|string} dayId - 学习计划中某一天的 ID
 * @param {{ question: string, referenceAnswer: string, userAnswer: string }} payload
 * @returns {Promise<{question:string,referenceAnswer:string,userAnswer:string,score?:number,mistakeType?:string,feedback?:string,nextRecommendation?:string}>}
 */
export async function evaluateExerciseByDay(dayId, payload) {
  const res = await fetch(
    `${API_BASE_URL}/api/plan/day/${dayId}/exercise-evaluate`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    }
  );

  if (!res.ok) {
    throw new Error(`评测练习题失败，状态码：${res.status}`);
  }

  return res.json();
}

/**
 * 查询练习记录列表，用于练习回顾页。
 * 对应后端 GET /api/exercise-records?userId=...&planId=...&dayId=...&limit=...
 * @param {{ userId: number, planId?: number|string, dayId?: number|string, limit?: number }} params
 * @returns {Promise<{summary:{totalRecords:number,scoredRecords:number,averageScore?:number,highestScore?:number,latestScore?:number,masteredCount:number,needsReviewCount:number},items:Array}>}
 */
export async function getExerciseRecords(params) {
  const query = new URLSearchParams();
  query.append('userId', String(params.userId));
  if (params.planId != null && params.planId !== '') {
    query.append('planId', String(params.planId));
  }
  if (params.dayId != null && params.dayId !== '') {
    query.append('dayId', String(params.dayId));
  }
  if (params.limit != null) {
    query.append('limit', String(params.limit));
  }

  const res = await fetch(`${API_BASE_URL}/api/exercise-records?${query.toString()}`);
  if (!res.ok) {
    throw new Error(`获取练习记录失败，状态码：${res.status}`);
  }
  return res.json();
}

/**
 * 删除单条练习记录。
 * 对应后端 DELETE /api/exercise-records/{recordId}?userId=...
 * @param {number|string} recordId
 * @param {number|string} userId
 */
export async function deleteExerciseRecord(recordId, userId) {
  const query = new URLSearchParams();
  query.append('userId', String(userId));

  const res = await fetch(
    `${API_BASE_URL}/api/exercise-records/${recordId}?${query.toString()}`,
    {
      method: 'DELETE'
    }
  );

  if (!res.ok) {
    throw new Error(`删除练习记录失败，状态码：${res.status}`);
  }
}

/**
 * 清空某个学习日下的练习记录。
 * 对应后端 DELETE /api/exercise-records/day/{dayId}?userId=...
 * @param {number|string} dayId
 * @param {number|string} userId
 * @returns {Promise<{success:boolean,dayId:number,deletedCount:number}>}
 */
export async function deleteExerciseRecordsByDay(dayId, userId) {
  const query = new URLSearchParams();
  query.append('userId', String(userId));

  const res = await fetch(
    `${API_BASE_URL}/api/exercise-records/day/${dayId}?${query.toString()}`,
    {
      method: 'DELETE'
    }
  );

  if (!res.ok) {
    throw new Error(`清空当日练习记录失败，状态码：${res.status}`);
  }

  return res.json();
}

/**
 * 保存某一天的一条练习记录（用户作答 + AI 评测结果）。
 * 对应后端 POST /api/plan/day/{dayId}/exercise-records
 * @param {number|string} dayId - 学习计划中某一天的 ID
 * @param {{ userId?: number, question: string, answer: string, explanation?: string, difficulty?: string, skillFocus?: string, userAnswer: string, aiScore?: number, aiMistakeType?: string, aiFeedback?: string, aiNextRecommendation?: string }} payload
 */
export async function saveExerciseRecord(dayId, payload) {
  const res = await fetch(
    `${API_BASE_URL}/api/plan/day/${dayId}/exercise-records`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    }
  );

  if (!res.ok) {
    throw new Error(`保存练习记录失败，状态码：${res.status}`);
  }
}

/**
 * 删除整份学习计划（当前实现为软删）。
 * 对应后端 DELETE /api/plan/{id}?userId=...
 * @param {number|string} id - 计划 ID
 * @param {number} userId - 当前登录用户 ID
 */
export async function deletePlan(id, userId) {
  const query = new URLSearchParams();
  if (userId != null) {
    query.append('userId', String(userId));
  }
  const url =
    query.toString().length > 0
      ? `${API_BASE_URL}/api/plan/${id}?${query.toString()}`
      : `${API_BASE_URL}/api/plan/${id}`;

  const res = await fetch(url, {
    method: 'DELETE'
  });

  if (!res.ok) {
    throw new Error(`删除学习计划失败，状态码：${res.status}`);
  }
}

/**
 * 更新整份学习计划（目前支持修改标题与状态）。
 * 对应后端 PATCH /api/plan/{id}?userId=...
 * @param {number|string} id - 计划 ID
 * @param {number} userId - 当前登录用户 ID
 * @param {{ title?: string, status?: string }} payload
 */
export async function updatePlan(id, userId, payload) {
  const query = new URLSearchParams();
  if (userId != null) {
    query.append('userId', String(userId));
  }
  const url =
    query.toString().length > 0
      ? `${API_BASE_URL}/api/plan/${id}?${query.toString()}`
      : `${API_BASE_URL}/api/plan/${id}`;

  const res = await fetch(url, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    throw new Error(`更新学习计划失败，状态码：${res.status}`);
  }
}

/**
 * 查询多 Agent 调用日志。
 * 对应后端 GET /api/agent/logs?traceId=...&limit=...
 * @param {{ traceId?: string, limit?: number }} params
 * @returns {Promise<Array<{id:number,traceId:string,agentName:string,durationMs:number,createdAt:string,requestPayload?:string,responsePayload?:string,modelName?:string}>>}
 */
export async function getAgentLogs(params = {}) {
  const query = new URLSearchParams();
  if (params.traceId) {
    query.append('traceId', params.traceId);
  }
  if (params.limit) {
    query.append('limit', String(params.limit));
  }
  const qs = query.toString();
  const url = `${API_BASE_URL}/api/agent/logs${qs ? `?${qs}` : ''}`;

  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`获取 Agent 调用日志失败，状态码：${res.status}`);
  }
  return res.json();
}







