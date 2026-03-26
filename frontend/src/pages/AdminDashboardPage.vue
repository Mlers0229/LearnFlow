<template>
  <div class="admin-dashboard-page">
    <section class="dashboard-hero">
      <div>
        <div class="dashboard-kicker">Admin Dashboard</div>
        <h1 class="dashboard-title">管理端 Dashboard</h1>
        <p class="dashboard-subtitle">这个页面作为管理端总览入口，把资源审核、用户运营、Agent 调用运维和待办工作收在同一个控制面板里。现在已经优先接入资源、用户、计划与日志接口，形成可持续使用的第一版动态总览。</p>
        <div class="dashboard-status-row">
          <span class="header-chip header-chip-admin-page">{{ loading ? '正在同步数据' : '动态数据已接入' }}</span>
          <span class="header-chip header-chip-admin-subtle">已接入：资源 / 用户 / 计划 / Agent 日志 / 模型配置</span>
          <span class="header-chip header-chip-admin-subtle">默认模型：{{ dashboardModelText }}</span>
        </div>
        <div v-if="error" class="dashboard-error">
          <span>{{ error }}</span>
          <n-button size="small" tertiary type="primary" @click="loadDashboard">重试</n-button>
        </div>
      </div>

      <div class="dashboard-focus-card">
        <div class="focus-label">当前视图目标</div>
        <div class="focus-title">让管理端从“工具页集合”升级成“运营控制台”</div>
        <div class="focus-desc">先展示系统健康、待处理事项和风险信号，再向下钻取到资源、用户、日志三条主线。后续可以继续把每张卡片接成详情页、抽屉或快捷筛选入口。</div>
        <div class="focus-meta">
          <span class="header-chip header-chip-admin-page">总览入口</span>
          <span class="header-chip header-chip-admin-subtle">动态卡片 + 字段设计</span>
        </div>
        <div class="focus-actions">
          <n-button size="small" secondary @click="loadDashboard">刷新总览</n-button>
        </div>
      </div>
    </section>

    <section class="metrics-grid">
      <n-card
        v-for="card in overviewCards"
        :key="card.key"
        size="small"
        :class="['metric-card', isInteractive(card) && 'panel-card-interactive']"
        :role="isInteractive(card) ? 'button' : undefined"
        :tabindex="isInteractive(card) ? 0 : -1"
        @click="openTarget(card)"
        @keyup.enter="openTarget(card)"
      >
        <div class="metric-label">{{ card.label }}</div>
        <div class="metric-value">{{ card.value }}</div>
        <div class="metric-trend">{{ card.trend }}</div>
        <div class="metric-fields">
          <span v-for="field in card.fields" :key="field" class="field-chip">{{ field }}</span>
        </div>
        <div v-if="card.actionLabel" class="card-action-hint">{{ card.actionLabel }}</div>
      </n-card>
    </section>

    <section class="dashboard-main-grid">
      <n-card size="small" class="dashboard-panel">
        <template #header>
          <div class="panel-head">
            <div>
              <div class="panel-kicker">风险预警</div>
              <div class="panel-title">需要管理员立即注意的问题</div>
            </div>
            <span class="panel-note">对应 Dashboard 的预警卡区</span>
          </div>
        </template>

        <div class="list-stack">
          <article
            v-for="item in riskItems"
            :key="item.title"
            :class="['list-item', isInteractive(item) && 'panel-card-interactive']"
            :role="isInteractive(item) ? 'button' : undefined"
            :tabindex="isInteractive(item) ? 0 : -1"
            @click="openTarget(item)"
            @keyup.enter="openTarget(item)"
          >
            <div class="list-item-top">
              <div class="list-item-title">{{ item.title }}</div>
              <n-tag size="small" :type="item.tagType">{{ item.tag }}</n-tag>
            </div>
            <div class="list-item-desc">{{ item.desc }}</div>
            <div class="list-item-fields">
              <span v-for="field in item.fields" :key="field" class="field-chip">{{ field }}</span>
            </div>
            <div v-if="item.actionLabel" class="card-action-hint">{{ item.actionLabel }}</div>
          </article>
        </div>
      </n-card>

      <n-card size="small" class="dashboard-panel">
        <template #header>
          <div class="panel-head">
            <div>
              <div class="panel-kicker">运营趋势</div>
              <div class="panel-title">推荐表现与系统运行趋势</div>
            </div>
            <span class="panel-note">对应后续折线图 / 柱状图占位</span>
          </div>
        </template>

        <div class="trend-stack">
          <div v-for="trend in trendCards" :key="trend.title" class="trend-card">
            <div class="trend-card-top">
              <div class="trend-title">{{ trend.title }}</div>
              <div class="trend-value">{{ trend.value }}</div>
            </div>
            <div class="trend-desc">{{ trend.desc }}</div>
            <div class="trend-fields">
              <span v-for="field in trend.fields" :key="field" class="field-chip">{{ field }}</span>
            </div>
          </div>
        </div>
      </n-card>
    </section>

    <section class="dashboard-secondary-grid">
      <n-card size="small" class="dashboard-panel">
        <template #header>
          <div class="panel-head">
            <div>
              <div class="panel-kicker">待办工作</div>
              <div class="panel-title">今天最值得先处理的事</div>
            </div>
            <span class="panel-note">可直接映射为管理端快捷入口</span>
          </div>
        </template>

        <div class="todo-grid">
          <div
            v-for="todo in todoCards"
            :key="todo.title"
            :class="['todo-card', isInteractive(todo) && 'panel-card-interactive']"
            :role="isInteractive(todo) ? 'button' : undefined"
            :tabindex="isInteractive(todo) ? 0 : -1"
            @click="openTarget(todo)"
            @keyup.enter="openTarget(todo)"
          >
            <div class="todo-card-title">{{ todo.title }}</div>
            <div class="todo-card-value">{{ todo.value }}</div>
            <div class="todo-card-desc">{{ todo.desc }}</div>
            <div v-if="todo.actionLabel" class="card-action-hint">{{ todo.actionLabel }}</div>
          </div>
        </div>
      </n-card>

      <n-card size="small" class="dashboard-panel">
        <template #header>
          <div class="panel-head">
            <div>
              <div class="panel-kicker">模块演进</div>
              <div class="panel-title">后续功能拓展的主干模块</div>
            </div>
            <span class="panel-note">结合你现在的资源 / 用户 / 日志页往下扩</span>
          </div>
        </template>

        <div class="module-stack">
          <article
            v-for="module in modules"
            :key="module.name"
            :class="['module-card', isInteractive(module) && 'panel-card-interactive']"
            :role="isInteractive(module) ? 'button' : undefined"
            :tabindex="isInteractive(module) ? 0 : -1"
            @click="openTarget(module)"
            @keyup.enter="openTarget(module)"
          >
            <div class="module-top">
              <div>
                <div class="module-name">{{ module.name }}</div>
                <div class="module-desc">{{ module.desc }}</div>
              </div>
              <n-tag size="small" :type="module.tagType">{{ module.priority }}</n-tag>
            </div>
            <div class="module-fields">
              <span v-for="field in module.fields" :key="field" class="field-chip">{{ field }}</span>
            </div>
            <div v-if="module.actionLabel" class="card-action-hint">{{ module.actionLabel }}</div>
          </article>
        </div>
      </n-card>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { fetchAdminDashboardSummary } from '../api/adminDashboard';

const router = useRouter();
const loading = ref(false);
const error = ref('');
const resources = ref([]);
const qualityStats = ref([]);
const users = ref([]);
const logs = ref([]);
const recentPlans = ref([]);
const feedbackTrend = ref([]);
const modelConfig = ref(null);

const resourceStatsById = computed(() => {
  const map = new Map();
  qualityStats.value.forEach((item) => {
    map.set(item.resourceId, item);
  });
  return map;
});

const enrichedResources = computed(() =>
  resources.value.map((item) => {
    const stat = resourceStatsById.value.get(item.id) || {};
    return {
      ...item,
      avgRating: stat.avgRating ?? null,
      feedbackCount: stat.feedbackCount ?? 0,
      invalidReportCount: stat.invalidReportCount ?? 0
    };
  })
);

const pendingResources = computed(() =>
  enrichedResources.value.filter((item) => (item.status || 'PENDING') === 'PENDING')
);

const activeUsers = computed(() =>
  users.value.filter((item) => (item.status || 'ACTIVE') === 'ACTIVE')
);

const disabledUsers = computed(() =>
  users.value.filter((item) => item.status === 'DISABLED')
);

const logs24h = computed(() => {
  const now = Date.now();
  const threshold = now - 24 * 60 * 60 * 1000;
  return logs.value.filter((item) => {
    const time = safeTime(item.createdAt);
    return time != null && time >= threshold;
  });
});

const avgLogDuration24h = computed(() => {
  if (!logs24h.value.length) return 0;
  return (
    logs24h.value.reduce((sum, item) => sum + Number(item.durationMs || 0), 0) /
    logs24h.value.length
  );
});

const suspiciousLogs = computed(() =>
  logs24h.value.filter((item) => isSuspiciousLog(item))
);

const topRiskResource = computed(() => {
  const list = [...enrichedResources.value].sort(
    (a, b) => Number(b.invalidReportCount || 0) - Number(a.invalidReportCount || 0)
  );
  return list[0] || null;
});

const latestRiskLog = computed(() => suspiciousLogs.value[0] || logs24h.value[0] || null);

const recentPlanCount7d = computed(() => {
  const now = Date.now();
  const threshold = now - 7 * 24 * 60 * 60 * 1000;
  return recentPlans.value.filter((item) => {
    const time = safeTime(item.createdAt || item.updatedAt || item.startDate);
    return time != null && time >= threshold;
  }).length;
});

const dashboardModelText = computed(() => modelConfig.value?.defaultModel || '尚未配置');
const dashboardModelConfigured = computed(() => Boolean(modelConfig.value?.configured));
const dashboardModelCatalogCount = computed(() => modelConfig.value?.catalog?.models?.length || 0);
const dashboardModelCatalogSource = computed(() => {
  const source = modelConfig.value?.catalog?.source;
  if (source === 'remote') return '远端目录';
  if (source === 'manual') return '手动目录';
  return '默认回退';
});

const overviewCards = computed(() => [
  {
    key: 'pendingResources',
    label: '待审核资源',
    value: formatNumber(pendingResources.value.length),
    trend: `资源总数 ${formatNumber(resources.value.length)}，已上线 ${formatNumber(
      enrichedResources.value.filter((item) => item.status === 'ACTIVE').length
    )}`,
    fields: ['pendingResourceCount', 'resourceCount', 'activeResourceCount'],
    to: '/admin/resources',
    query: { status: 'PENDING' },
    actionLabel: '点击查看待审列表'
  },
  {
    key: 'activeUsers',
    label: '正常账号用户',
    value: formatNumber(activeUsers.value.length),
    trend: `禁用账号 ${formatNumber(disabledUsers.value.length)}，用户总数 ${formatNumber(
      users.value.length
    )}`,
    fields: ['activeUserCount', 'disabledUserCount', 'userCount'],
    to: '/admin/users',
    query: { status: 'ACTIVE' },
    actionLabel: '点击查看正常用户'
  },
  {
    key: 'agentCalls',
    label: '24h Agent 调用',
    value: formatNumber(logs24h.value.length),
    trend: `疑似异常 ${formatNumber(suspiciousLogs.value.length)} 次，平均耗时 ${Math.round(
      avgLogDuration24h.value
    )}ms`,
    fields: ['agentCallCount24h', 'agentErrorCount24h', 'avgAgentDuration24h'],
    to: '/admin/logs',
    query: { limit: '120' },
    actionLabel: '点击进入 Agent 日志'
  },
  {
    key: 'recentPlans',
    label: '近 7 天计划生成',
    value: formatNumber(recentPlanCount7d.value),
    trend: `最近拉取 ${formatNumber(recentPlans.value.length)} 条计划样本`,
    fields: ['recentPlanCount7d', 'planGeneratedCount', 'createdAt']
  },
  {
    key: 'modelControl',
    label: '当前默认模型',
    value: dashboardModelText.value,
    trend: dashboardModelConfigured.value
      ? `目录 ${formatNumber(dashboardModelCatalogCount.value)} 个，来源 ${dashboardModelCatalogSource.value}`
      : '管理端尚未完成模型接入',
    fields: ['defaultModel', 'catalogCount', 'catalogSource', 'configured'],
    to: '/admin/models',
    actionLabel: '点击进入模型配置'
  }
]);

const riskItems = computed(() => [
  {
    title: dashboardModelConfigured.value ? '模型配置已接入管理端' : '模型配置尚未完整接入',
    tag: dashboardModelConfigured.value ? '模型状态' : '待处理',
    tagType: dashboardModelConfigured.value ? 'success' : 'warning',
    desc: dashboardModelConfigured.value
      ? `当前默认模型为 ${dashboardModelText.value}，目录来源 ${dashboardModelCatalogSource.value}，共 ${formatNumber(dashboardModelCatalogCount.value)} 个模型。`
      : '请先补齐 API Base / API Key，并确认模型目录同步成功后再让用户侧使用统一分配模型。',
    fields: ['configured', 'defaultModel', 'catalogSource', 'catalogCount'],
    to: '/admin/models',
    actionLabel: '点击查看模型配置'
  },
  topRiskResource.value
    ? {
        title: `高举报资源：${topRiskResource.value.title || '未命名资源'}`,
        tag: '资源风险',
        tagType: 'warning',
        desc: `举报 ${formatNumber(topRiskResource.value.invalidReportCount || 0)} 次，平均评分 ${
          topRiskResource.value.avgRating == null ? '暂无' : Number(topRiskResource.value.avgRating).toFixed(1)
        }。适合后续接入资源详情抽屉与反馈明细。`,
        fields: ['resourceId', 'title', 'invalidReportCount', 'avgRating', 'feedbackCount'],
        to: '/admin/resources',
        query: { risk: 'reported', keyword: topRiskResource.value.title || '' },
        actionLabel: '点击定位这条资源'
      }
    : {
        title: '高举报资源 Top 1',
        tag: '资源风险',
        tagType: 'warning',
        desc: '当前还没有可用资源反馈数据，待资源反馈累积后即可自动进入这个卡位。',
        fields: ['resourceId', 'title', 'invalidReportCount', 'avgRating', 'feedbackCount']
      },
  latestRiskLog.value
    ? {
        title: `最近异常 / 慢调用：${latestRiskLog.value.agentName || '未知 Agent'}`,
        tag: '调用异常',
        tagType: suspiciousLogs.value.length ? 'error' : 'warning',
        desc: `traceId ${latestRiskLog.value.traceId || '—'}，耗时 ${formatNumber(
          latestRiskLog.value.durationMs || 0
        )}ms，可继续进入日志页按 trace 排查。`,
        fields: ['traceId', 'agentName', 'durationMs', 'requestPayload', 'responsePayload'],
        to: '/admin/logs',
        query: latestRiskLog.value.traceId
          ? { traceId: String(latestRiskLog.value.traceId), limit: '120' }
          : { mode: 'suspicious', limit: '120' },
        actionLabel: '点击查看对应日志'
      }
    : {
        title: '最近错误 Trace',
        tag: '调用异常',
        tagType: 'error',
        desc: '当前没有拿到日志数据，后续可以继续接 trace 详情和错误类型统计。',
        fields: ['traceId', 'agentName', 'durationMs', 'errorMessage', 'createdAt']
      },
  {
    title: '待补齐学习画像接口',
    tag: '接口待扩展',
    tagType: 'info',
    desc: '当前管理端还没有全局练习记录和最近活跃轨迹接口，后续可补 avgExerciseScore、needsReviewCount、lastActiveAt 等字段。',
    fields: ['lastActiveAt', 'avgExerciseScore', 'needsReviewCount', 'planCount']
  }
]);

const trendCards = computed(() => {
  const lastFeedback = feedbackTrend.value.length ? feedbackTrend.value[feedbackTrend.value.length - 1] : null;
  return [
    {
      title: '资源反馈趋势',
      value: `${feedbackTrend.value.length} 天`,
      desc: lastFeedback
        ? `最近一天反馈 ${formatNumber(lastFeedback.feedbackCount || 0)} 条，举报 ${formatNumber(
            lastFeedback.invalidReportCount || 0
          )} 次。`
        : '当前还没有反馈趋势数据，后续可以补图表组件直接渲染折线趋势。',
      fields: ['date', 'feedbackCount', 'invalidReportCount', 'avgRating']
    },
    {
      title: '学习计划趋势',
      value: `${groupCountByDay(recentPlans.value).length} 天`,
      desc: recentPlans.value.length > 0
        ? `最近 7 天共拉取 ${formatNumber(recentPlanCount7d.value)} 条计划，适合后续和练习提交量合并成学习活跃趋势。`
        : '当前没有计划样本，后续可结合计划生成、练习提交和活跃用户做综合运营图。',
      fields: ['date', 'planGeneratedCount', 'exerciseSubmittedCount', 'activeUserCount']
    },
    {
      title: 'Agent 调用健康度',
      value: `${groupCountByHour(logs24h.value).length} 桶`,
      desc: logs24h.value.length > 0
        ? `24 小时内共 ${formatNumber(logs24h.value.length)} 次调用，平均耗时 ${Math.round(avgLogDuration24h.value)}ms。`
        : '当前没有 24 小时内日志样本，后续可按小时分桶渲染调用量与耗时变化。',
      fields: ['timestampBucket', 'callCount', 'errorCount', 'avgDurationMs']
    }
  ];
});

const todoCards = computed(() => [
  {
    title: '待审资源',
    value: formatNumber(pendingResources.value.length),
    desc: '优先跳到资源管理页，按“待审核”筛选集中处理。',
    to: '/admin/resources',
    query: { status: 'PENDING' },
    actionLabel: '点击进入审核队列'
  },
  {
    title: '高举报处理',
    value: formatNumber(
      enrichedResources.value.filter((item) => Number(item.invalidReportCount || 0) > 0).length
    ),
    desc: '后续可直接打开资源反馈明细与审核历史。',
    to: '/admin/resources',
    query: { risk: 'reported' },
    actionLabel: '点击筛出高风险资源'
  },
  {
    title: '异常 / 慢调用',
    value: formatNumber(suspiciousLogs.value.length),
    desc: '跳到调用日志页，优先按 traceId 和耗时定位问题。',
    to: '/admin/logs',
    query: { mode: 'suspicious', limit: '120' },
    actionLabel: '点击查看异常日志'
  },
  {
    title: '待补充画像',
    value: '2',
    desc: '补全全局练习记录与活跃轨迹接口，完善用户运营视图。'
  },
  {
    title: '模型配置检查',
    value: dashboardModelConfigured.value ? dashboardModelText.value : '待配置',
    desc: dashboardModelConfigured.value
      ? '统一模型策略已托管到管理端，可继续检查目录同步和计划生成策略。'
      : '优先检查 API Base、API Key 和目录同步状态。',
    to: '/admin/models',
    actionLabel: '点击进入模型配置'
  }
]);

const modules = [
  {
    name: '资源审核中心',
    desc: '从当前的“资源列表 + 上下线操作”升级为“列表 + 详情 + 审核历史”。',
    priority: 'P0',
    tagType: 'error',
    fields: ['pendingResourceCount', 'topReportedResources', 'reviewHistory', 'reviewComment'],
    to: '/admin/resources',
    actionLabel: '进入资源审核中心'
  },
  {
    name: 'Agent 运维面板',
    desc: '从当前的调试表格升级为可过滤、可聚合、可钻取 trace 的运维台。',
    priority: 'P0',
    tagType: 'warning',
    fields: ['agentErrorCount24h', 'slowTraceTopN', 'traceDetail', 'agentStatsByName'],
    to: '/admin/logs',
    actionLabel: '进入 Agent 运维面板'
  },
  {
    name: '用户学习画像',
    desc: '在角色和账号状态之外，追加学习轨迹、活跃度和复习风险指标。',
    priority: 'P1',
    tagType: 'info',
    fields: ['lastLoginAt', 'lastActiveAt', 'planCount', 'avgExerciseScore', 'needsReviewCount'],
    to: '/admin/users',
    actionLabel: '进入用户中心'
  },
  {
    name: '系统配置与审计',
    desc: '后续可控制推荐阈值、Agent 参数、功能开关和管理员操作审计。',
    priority: 'P1',
    tagType: 'success',
    fields: ['settingKey', 'settingValue', 'updatedBy', 'auditLogs']
  }
];

onMounted(() => {
  loadDashboard();
});

async function loadDashboard() {
  loading.value = true;
  error.value = '';
  try {
    const data = await fetchAdminDashboardSummary({
      logLimit: 120,
      planLimit: 50,
      trendDays: 7
    });
    resources.value = Array.isArray(data?.resources) ? data.resources : [];
    qualityStats.value = Array.isArray(data?.resourceQualityStats) ? data.resourceQualityStats : [];
    users.value = Array.isArray(data?.users) ? data.users : [];
    logs.value = Array.isArray(data?.agentLogs) ? data.agentLogs : [];
    recentPlans.value = Array.isArray(data?.recentPlans) ? data.recentPlans : [];
    feedbackTrend.value = Array.isArray(data?.feedbackTrend) ? data.feedbackTrend : [];
    modelConfig.value = data?.modelConfig || null;
  } catch (err) {
    console.error(err);
    error.value = err.message || '管理端总览加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function isInteractive(item) {
  return Boolean(item?.to);
}

function openTarget(item) {
  if (!item?.to) return;
  router.push({
    path: item.to,
    query: item.query
  });
}

function isSuspiciousLog(row) {
  const haystack = `${row?.requestPayload || ''} ${row?.responsePayload || ''}`.toLowerCase();
  return (
    Number(row?.durationMs || 0) >= 3000 ||
    haystack.includes('error') ||
    haystack.includes('exception') ||
    haystack.includes('fail')
  );
}

function safeTime(value) {
  if (!value) return null;
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? null : time;
}

function formatNumber(value) {
  return new Intl.NumberFormat('zh-CN').format(Number(value || 0));
}

function normalizeDateLabel(value) {
  const time = safeTime(value);
  if (time == null) return '';
  return new Date(time).toISOString().slice(0, 10);
}

function groupCountByDay(list) {
  const counter = new Map();
  list.forEach((item) => {
    const date = normalizeDateLabel(item.createdAt || item.updatedAt || item.startDate);
    if (!date) return;
    counter.set(date, (counter.get(date) || 0) + 1);
  });
  return [...counter.entries()].map(([date, count]) => ({ date, count }));
}

function groupCountByHour(list) {
  const counter = new Map();
  list.forEach((item) => {
    const time = safeTime(item.createdAt);
    if (time == null) return;
    const bucket = new Date(time).toISOString().slice(0, 13);
    counter.set(bucket, (counter.get(bucket) || 0) + 1);
  });
  return [...counter.entries()].map(([bucket, count]) => ({ bucket, count }));
}
</script>

<style scoped>
.admin-dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dashboard-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(280px, 0.7fr);
  gap: 16px;
  align-items: stretch;
  padding: 22px 24px;
  border-radius: 24px;
  border: 1px solid rgba(17, 42, 59, 0.08);
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.82), transparent 35%),
    linear-gradient(135deg, #f9fcfc, #f1f6f4 55%, #eef3f8);
}

.dashboard-kicker,
.panel-kicker {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  color: #6f8191;
}

.dashboard-title {
  margin: 6px 0 0;
  font-family: 'Georgia', 'Times New Roman', serif;
  font-size: 32px;
  line-height: 1.08;
  color: #102235;
}

.dashboard-subtitle {
  margin: 10px 0 0;
  max-width: 820px;
  font-size: 13px;
  line-height: 1.8;
  color: #607483;
}

.dashboard-status-row {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dashboard-error {
  margin-top: 12px;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 248, 235, 0.92);
  border: 1px solid rgba(245, 158, 11, 0.24);
  color: #8a5a08;
  font-size: 12px;
}

.dashboard-focus-card {
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, #173850, #2b6661 125%);
  color: #f8fafc;
  box-shadow: 0 16px 28px rgba(15, 41, 64, 0.16);
}

.focus-label {
  font-size: 12px;
  color: rgba(243, 249, 251, 0.72);
}

.focus-title {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.25;
}

.focus-desc {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: rgba(243, 249, 251, 0.84);
}

.focus-meta {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.focus-actions {
  margin-top: 14px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.metric-card,
.dashboard-panel {
  border-radius: 20px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.metric-card {
  background: linear-gradient(180deg, #ffffff, #f7fbfb);
}

.metric-label {
  font-size: 12px;
  color: #6b7280;
}

.metric-value {
  margin-top: 4px;
  font-size: 28px;
  font-weight: 700;
  color: #102235;
}

.metric-trend {
  margin-top: 4px;
  font-size: 12px;
  color: #7c8b98;
}

.metric-fields,
.list-item-fields,
.trend-fields,
.module-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.field-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 9px;
  border-radius: 999px;
  background: #eff5f6;
  color: #45606f;
  font-size: 11px;
  font-family:
    ui-monospace,
    SFMono-Regular,
    Menlo,
    Monaco,
    Consolas,
    'Liberation Mono',
    'Courier New',
    monospace;
}

.card-action-hint {
  margin-top: 12px;
  font-size: 11px;
  letter-spacing: 0.04em;
  color: #1b5c73;
}

.dashboard-main-grid,
.dashboard-secondary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-title {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
  color: #102235;
}

.panel-note {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.7;
}

.list-stack,
.trend-stack,
.module-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.list-item,
.trend-card,
.module-card,
.todo-card {
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfcfd, #f7fafb);
  border: 1px solid rgba(148, 163, 184, 0.22);
}

.list-item-top,
.trend-card-top,
.module-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.list-item-title,
.trend-title,
.module-name,
.todo-card-title {
  font-size: 15px;
  font-weight: 700;
  color: #102235;
}

.list-item-desc,
.trend-desc,
.module-desc,
.todo-card-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.75;
  color: #607483;
}

.trend-value,
.todo-card-value {
  font-size: 20px;
  font-weight: 700;
  color: #173850;
}

.todo-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.panel-card-interactive {
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.panel-card-interactive:hover,
.panel-card-interactive:focus-visible {
  transform: translateY(-2px);
  box-shadow: 0 16px 30px rgba(17, 42, 59, 0.1);
  border-color: rgba(43, 102, 97, 0.28);
  outline: none;
}

@media (max-width: 1100px) {
  .dashboard-hero,
  .metrics-grid,
  .dashboard-main-grid,
  .dashboard-secondary-grid,
  .todo-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .panel-head,
  .list-item-top,
  .trend-card-top,
  .module-top,
  .dashboard-error {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
