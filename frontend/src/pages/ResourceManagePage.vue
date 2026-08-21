<template>
  <div class="resource-page">
    <section class="resource-hero">
      <div>
        <div class="page-kicker">资源运营台</div>
        <h1 class="page-title">资源管理</h1>
        <p class="page-desc">把资源入库、待审队列、风险反馈和批量上下线收在同一页。这版重点是让管理端能更快地看到待处理压力、风险资源和筛选上下文。</p>
        <div class="hero-meta">
          <span class="header-chip header-chip-admin-page">资源审核中心</span>
          <span class="header-chip header-chip-admin-subtle">入库 + 审核 + 质量回路</span>
        </div>
      </div>

      <div class="hero-side-card">
        <div class="hero-side-label">当前运维建议</div>
        <div class="hero-side-title">先处理待审和高风险资源</div>
        <div class="hero-side-desc">如果总举报数上升，可以先用“仅看有举报”筛选出风险资源，再结合标题检索进行定位。</div>
        <div class="hero-side-actions">
          <n-button size="small" secondary @click="focusPending">只看待审</n-button>
          <n-button size="small" tertiary type="primary" @click="focusRisky">只看高风险</n-button>
        </div>
      </div>
    </section>

    <section class="summary-grid">
      <article v-for="card in summaryCards" :key="card.label" class="summary-card">
        <div class="summary-label">{{ card.label }}</div>
        <div class="summary-value">{{ card.value }}</div>
        <div class="summary-desc">{{ card.desc }}</div>
      </article>
    </section>

    <div class="resource-layout">
      <n-card class="resource-form-card" size="small">
        <template #header>
          <div class="card-head">
            <div>
              <div class="card-kicker">快速入库</div>
              <div class="card-title">录入学习资源</div>
            </div>
            <span class="header-chip">提交后默认进入待审</span>
          </div>
        </template>

        <p class="subtitle">在这里可以把你觉得不错的中文学习资源（视频 / 文章 / 文档）录入到系统的资源库中，后续 RAG 推荐会优先从这些资源中进行匹配。</p>

        <div class="form-note-row">
          <span class="field-chip">标题</span>
          <span class="field-chip">链接</span>
          <span class="field-chip">领域</span>
          <span class="field-chip">水平</span>
          <span class="field-chip">时长</span>
          <span class="field-chip">标签</span>
        </div>

        <n-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-placement="top"
          size="small"
          @submit.prevent="onSubmit"
        >
          <div class="form-grid">
            <n-form-item label="资源标题（必填）" path="title">
              <n-input v-model:value="form.title" placeholder="例如：Java 基础语法入门（B 站视频）" />
            </n-form-item>

            <n-form-item label="资源链接 URL（必填）" path="url">
              <n-input v-model:value="form.url" placeholder="例如：https://www.bilibili.com/..." />
            </n-form-item>

            <n-form-item label="适用水平" path="level">
              <n-select v-model:value="form.level" :options="levelOptions" placeholder="不限" />
            </n-form-item>

            <n-form-item label="资源领域（必填）" path="domain">
              <n-select v-model:value="form.domain" :options="domainOptions" placeholder="选择资源领域" />
            </n-form-item>

            <n-form-item label="预计学习时长" path="duration">
              <div class="duration-row">
                <n-input-number v-model:value="form.durationHours" :min="0" size="small" placeholder="小时" class="duration-input" />
                <span class="duration-separator">小时</span>
                <n-input-number v-model:value="form.durationMinutes" :min="0" size="small" placeholder="分钟（可选）" class="duration-input" />
                <span class="duration-separator">分钟</span>
              </div>
            </n-form-item>

            <n-form-item label="标签（逗号分隔）" path="tags" class="form-full">
              <n-input v-model:value="form.tags" placeholder="例如：java,basic,bilibili" />
            </n-form-item>

            <div class="form-full form-actions">
              <n-button type="primary" block attr-type="submit" :loading="loading">
                {{ loading ? '正在提交…' : '提交资源' }}
              </n-button>
            </div>
          </div>
        </n-form>

        <n-alert v-if="error" type="error" closable class="form-alert" @close="error = ''">{{ error }}</n-alert>
        <n-alert v-if="success" type="success" closable class="form-alert" @close="success = ''">{{ success }}</n-alert>
      </n-card>

      <n-card class="resource-table-card" size="small">
        <template #header>
          <div class="card-head card-head-spread">
            <div>
              <div class="card-kicker">资源看板</div>
              <div class="card-title">当前资源库中的资源</div>
            </div>
            <div class="table-head-meta">
              <span class="header-chip header-chip-admin-page">当前显示 {{ filteredResources.length }} / {{ enrichedResources.length }}</span>
              <span class="header-chip header-chip-admin-subtle">批量上下线已就绪</span>
            </div>
          </div>
        </template>

        <p class="helper-text">RAG 推荐会从下面这些资源中按主题和难度进行匹配，右上角的运营概览可以帮你快速判断待审压力和资源质量。</p>

        <section class="quality-summary-grid">
          <div class="quality-stat-card">
            <div class="quality-label">有评分的资源数</div>
            <div class="quality-value">{{ qualityStatsSummary.ratedCount }}</div>
          </div>
          <div class="quality-stat-card">
            <div class="quality-label">平均评分（有评分资源）</div>
            <div class="quality-value">{{ qualityStatsSummary.avgOfAvgRatings.toFixed(1) }}</div>
          </div>
          <div class="quality-stat-card">
            <div class="quality-label">总举报次数</div>
            <div class="quality-value">{{ qualityStatsSummary.totalInvalidReports }}</div>
          </div>
        </section>
        <div class="filter-panel">
          <div class="filter-row">
            <n-input v-model:value="filterKeyword" size="small" clearable placeholder="按标题 / URL / 标签搜索" />
            <n-select v-model:value="filterDomain" :options="domainFilterOptions" size="small" clearable placeholder="按领域筛选" />
            <n-select v-model:value="filterLevel" :options="filterLevelOptions" size="small" clearable placeholder="按水平筛选" />
            <n-select v-model:value="filterStatus" :options="statusOptions" size="small" clearable placeholder="按状态筛选" />
            <n-select v-model:value="riskFilter" :options="riskOptions" size="small" clearable placeholder="按风险筛选" />
          </div>

          <div v-if="activeFilterChips.length" class="active-filters">
            <span class="active-filters-label">当前筛选</span>
            <button v-for="chip in activeFilterChips" :key="chip.key" class="filter-chip-button" type="button" @click="clearSingleFilter(chip.key)">
              {{ chip.label }}
            </button>
            <n-button size="tiny" quaternary @click="clearFilters">清空筛选</n-button>
          </div>

          <div class="toolbar-row">
            <div class="toolbar-hint">可对当前结果执行批量上下线、导出 CSV 或继续按关键词缩小范围。</div>
            <div class="toolbar-actions">
              <n-button size="small" secondary @click="exportCsv">导出 CSV（按当前筛选）</n-button>
              <n-button size="small" type="primary" secondary :disabled="!checkedRowKeys.length" @click="batchSet('ACTIVE')">批量上线</n-button>
              <n-button size="small" secondary :disabled="!checkedRowKeys.length" @click="batchSet('INACTIVE')">批量下线/拒绝</n-button>
            </div>
          </div>
        </div>

        <n-data-table
          :columns="columns"
          :data="filteredResources"
          :bordered="false"
          :single-line="false"
          size="small"
          :scroll-x="1320"
          checkable
          v-model:checked-row-keys="checkedRowKeys"
          :row-key="(row) => row.id"
          :pagination="{ pageSize: 10 }"
        />

        <div v-if="!filteredResources.length" class="empty-state">
          <div class="empty-title">当前筛选结果为空</div>
          <div class="empty-desc">可以清空筛选，或先从左侧录入一条新资源。</div>
          <div class="empty-actions">
            <n-button size="small" secondary @click="clearFilters">清空筛选</n-button>
          </div>
        </div>
      </n-card>
    </div>
  </div>

  <n-modal v-model:show="editModal" preset="dialog" title="编辑资源">
    <n-form label-placement="left" label-width="80">
      <n-form-item label="标题">
        <n-input v-model:value="editing.title" />
      </n-form-item>
      <n-form-item label="URL">
        <n-input v-model:value="editing.url" />
      </n-form-item>
      <n-form-item label="水平">
        <n-select v-model:value="editing.level" :options="levelOptions.slice(1)" clearable />
      </n-form-item>
      <n-form-item label="领域">
        <n-select v-model:value="editing.domain" :options="domainOptions" clearable />
      </n-form-item>
      <n-form-item label="时长(分钟)">
        <n-input-number v-model:value="editing.durationMinutes" :min="0" />
      </n-form-item>
      <n-form-item label="标签">
        <n-input v-model:value="editing.tags" />
      </n-form-item>
    </n-form>
    <template #action>
      <n-button @click="editModal = false">取消</n-button>
      <n-button type="primary" @click="saveEdit">保存</n-button>
    </template>
  </n-modal>
</template>

<script setup>
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NAlert, NButton, NTag, NModal, NForm, NFormItem, NInputNumber, NSelect, NInput } from 'naive-ui';
import { createResource, listResources, updateResourceStatus, getResourceQualityStats, batchUpdateResourceStatus, updateResource, reingestResourceUrl } from '../api/resource';

const route = useRoute();
const router = useRouter();

const form = reactive({
  title: '',
  url: '',
  domain: '',
  level: '',
  durationHours: null,
  durationMinutes: null,
  tags: ''
});

const levelOptions = [
  { label: '不限', value: '' },
  { label: '零基础', value: 'beginner' },
  { label: '有一点基础', value: 'intermediate' },
  { label: '进阶', value: 'advanced' }
];

const domainOptions = [
  { label: 'Java 后端', value: 'java' },
  { label: 'Python', value: 'python' },
  { label: '数据库 / SQL', value: 'database' },
  { label: '英语', value: 'english' },
  { label: '数学', value: 'math' },
  { label: '前端', value: 'frontend' },
  { label: 'Linux / 运维', value: 'devops' },
  { label: '通用学习方法', value: 'general' }
];

const filterLevelOptions = [
  { label: '全部水平', value: '' },
  { label: '零基础', value: 'beginner' },
  { label: '有一点基础', value: 'intermediate' },
  { label: '进阶', value: 'advanced' }
];

const domainFilterOptions = [
  { label: '全部领域', value: '' },
  ...domainOptions
];

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待审核', value: 'PENDING' },
  { label: '已上线', value: 'ACTIVE' },
  { label: '已下线', value: 'INACTIVE' }
];

const riskOptions = [
  { label: '全部风险', value: '' },
  { label: '仅看有举报', value: 'reported' }
];

const rules = {
  title: { required: true, message: '请填写资源标题', trigger: ['input', 'blur'] },
  url: { required: true, message: '请填写资源链接', trigger: ['input', 'blur'] },
  domain: { required: true, message: '请选择资源领域', trigger: ['change', 'blur'] }
};

const formRef = ref(null);
const loading = ref(false);
const error = ref('');
const success = ref('');
const resources = ref([]);
const qualityStats = ref([]);
const filterKeyword = ref('');
const filterDomain = ref('');
const filterLevel = ref('');
const filterStatus = ref('');
const riskFilter = ref('');
const checkedRowKeys = ref([]);
const editModal = ref(false);
const editing = reactive({ id: null, title: '', url: '', domain: '', level: '', durationMinutes: null, tags: '' });

const qualityStatsSummary = computed(() => {
  if (!qualityStats.value.length) {
    return { ratedCount: 0, avgOfAvgRatings: 0, totalInvalidReports: 0 };
  }
  const rated = qualityStats.value.filter((item) => item.avgRating != null);
  const ratedCount = rated.length;
  const avgOfAvgRatings = ratedCount > 0 ? rated.reduce((sum, item) => sum + item.avgRating, 0) / ratedCount : 0;
  const totalInvalidReports = qualityStats.value.reduce((sum, item) => sum + Number(item.invalidReportCount || 0), 0);
  return { ratedCount, avgOfAvgRatings, totalInvalidReports };
});

const qualityStatsByResourceId = computed(() => {
  const map = new Map();
  qualityStats.value.forEach((item) => {
    map.set(item.resourceId, item);
  });
  return map;
});

const enrichedResources = computed(() =>
  resources.value.map((item) => {
    const match = qualityStatsByResourceId.value.get(item.id);
    return {
      ...item,
      avgRating: match ? match.avgRating : null,
      feedbackCount: match ? match.feedbackCount : 0,
      invalidReportCount: match ? match.invalidReportCount : 0
    };
  })
);
const summaryCards = computed(() => [
  { label: '资源总量', value: enrichedResources.value.length, desc: '当前资源库中可管理的全部资源数。' },
  { label: '待审资源', value: enrichedResources.value.filter((item) => normalizeStatus(item.status) === 'PENDING').length, desc: '可优先处理这部分待审队列。' },
  { label: '高风险资源', value: enrichedResources.value.filter((item) => Number(item.invalidReportCount || 0) > 0).length, desc: '已有举报反馈，建议快速复查。' },
  { label: '资源均分', value: qualityStatsSummary.value.avgOfAvgRatings.toFixed(1), desc: '仅按已有评分的资源计算。' }
]);

const activeFilterChips = computed(() => {
  const chips = [];
  if (filterKeyword.value) chips.push({ key: 'keyword', label: `关键词：${filterKeyword.value}` });
  if (filterDomain.value) chips.push({ key: 'domain', label: `领域：${domainText(filterDomain.value)}` });
  if (filterLevel.value) chips.push({ key: 'level', label: `水平：${levelText(filterLevel.value)}` });
  if (filterStatus.value) chips.push({ key: 'status', label: `状态：${statusText(filterStatus.value)}` });
  if (riskFilter.value === 'reported') chips.push({ key: 'risk', label: '风险：仅看有举报' });
  return chips;
});

const filteredResources = computed(() =>
  enrichedResources.value.filter((item) => {
    if (filterDomain.value && String(item.domain || '') !== filterDomain.value) return false;
    if (filterLevel.value && item.level !== filterLevel.value) return false;
    if (filterStatus.value && normalizeStatus(item.status) !== filterStatus.value) return false;
    if (riskFilter.value === 'reported' && Number(item.invalidReportCount || 0) <= 0) return false;
    if (!filterKeyword.value) return true;
    const keyword = filterKeyword.value.toLowerCase();
    return (
      (item.title && item.title.toLowerCase().includes(keyword)) ||
      (item.url && item.url.toLowerCase().includes(keyword)) ||
      (item.tags && item.tags.toLowerCase().includes(keyword)) ||
      (item.domain && item.domain.toLowerCase().includes(keyword))
    );
  })
);

const columns = [
  { type: 'selection', width: 40 },
  {
    title: '资源',
    key: 'title',
    minWidth: 260,
    render(row) {
      return h('div', { class: 'table-title-cell' }, [
        row.url
          ? h('a', { href: row.url, target: '_blank', rel: 'noopener noreferrer', class: 'resource-link' }, row.title)
          : h('span', { class: 'resource-link' }, row.title),
        h('div', { class: 'table-title-meta' }, getDomainFromUrl(row.url) || `${row.sourceType || 'DOCUMENT'} · ${row.ingestionStatus || 'NOT_STARTED'}`)
      ]);
    }
  },
  {
    title: '领域',
    key: 'domain',
    width: 130,
    render(row) {
      return h(NTag, { size: 'small', type: 'info' }, { default: () => domainText(row.domain) });
    }
  },
  {
    title: '水平',
    key: 'level',
    width: 110,
    render(row) {
      return h(NTag, { size: 'small', type: levelTagType(row.level) }, { default: () => levelText(row.level) });
    }
  },
  {
    title: '时长',
    key: 'durationMinutes',
    width: 120,
    render(row) {
      return formatDuration(row.durationMinutes) || '—';
    }
  },
  {
    title: '标签',
    key: 'tags',
    minWidth: 180,
    render(row) {
      const tags = splitTags(row.tags);
      if (!tags.length) return '—';
      return h('div', { class: 'tag-stack' }, tags.slice(0, 3).map((tag) => h('span', { class: 'inline-tag-chip' }, tag)));
    }
  },
  {
    title: '质量',
    key: 'quality',
    width: 170,
    render(row) {
      return h('div', { class: 'quality-cell' }, [
        h('div', { class: 'quality-line' }, `评分 ${row.avgRating == null ? '—' : row.avgRating.toFixed(1)}`),
        h('div', { class: 'quality-subline' }, `反馈 ${row.feedbackCount || 0} / 举报 ${row.invalidReportCount || 0}`)
      ]);
    }
  },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render(row) {
      return h(NTag, { size: 'small', type: statusTagType(normalizeStatus(row.status)) }, { default: () => statusText(normalizeStatus(row.status)) });
    }
  },
  {
    title: '操作',
    key: 'actions',
    width: 240,
    render(row) {
      const buttons = [h(NButton, { size: 'tiny', quaternary: true, onClick: () => openEdit(row) }, { default: () => '编辑' })];
      if (row.url) buttons.push(h(NButton, { size: 'tiny', quaternary: true, onClick: () => reingestUrl(row) }, { default: () => '重新摄取' }));
      if (normalizeStatus(row.status) !== 'ACTIVE') buttons.push(h(NButton, { size: 'tiny', type: 'primary', quaternary: true, onClick: () => changeStatus(row, 'ACTIVE') }, { default: () => '审核通过并上线' }));
      if (normalizeStatus(row.status) === 'ACTIVE') buttons.push(h(NButton, { size: 'tiny', quaternary: true, onClick: () => changeStatus(row, 'INACTIVE') }, { default: () => '下线' }));
      if (normalizeStatus(row.status) === 'PENDING') buttons.push(h(NButton, { size: 'tiny', quaternary: true, type: 'error', onClick: () => changeStatus(row, 'INACTIVE') }, { default: () => '拒绝' }));
      return h('div', { class: 'table-actions' }, buttons);
    }
  }
];

onMounted(async () => {
  syncFiltersFromRoute();
  await Promise.all([loadResources(), loadQualityStats()]);
});

watch(() => route.query, (query) => {
  syncFiltersFromRoute(query);
});

function syncFiltersFromRoute(query = route.query) {
  filterKeyword.value = typeof query.keyword === 'string' ? query.keyword : '';
  filterDomain.value = typeof query.domain === 'string' ? query.domain : '';
  filterLevel.value = typeof query.level === 'string' && ['beginner', 'intermediate', 'advanced'].includes(query.level) ? query.level : '';
  filterStatus.value = typeof query.status === 'string' && ['PENDING', 'ACTIVE', 'INACTIVE'].includes(query.status) ? query.status : '';
  riskFilter.value = query.risk === 'reported' ? 'reported' : '';
}

async function loadResources() {
  error.value = '';
  try {
    resources.value = await listResources();
  } catch (e) {
    console.error(e);
    error.value = '加载资源列表失败，请确认后端服务已启动（18081），然后重试。';
  }
}

async function loadQualityStats() {
  try {
    qualityStats.value = await getResourceQualityStats();
  } catch (e) {
    console.error('load quality stats failed', e);
  }
}

async function onSubmit() {
  if (formRef.value) {
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
  }

  error.value = '';
  success.value = '';
  loading.value = true;

  try {
    const totalMinutes = (form.durationHours || 0) * 60 + (form.durationMinutes || 0);
    await createResource({ title: form.title, url: form.url, domain: form.domain, level: form.level || null, durationMinutes: totalMinutes || null, tags: form.tags || null });
    success.value = '资源已提交成功！';
    form.title = '';
    form.url = '';
    form.domain = '';
    form.level = '';
    form.durationHours = null;
    form.durationMinutes = null;
    form.tags = '';
    await loadResources();
    await loadQualityStats();
  } catch (e) {
    console.error(e);
    error.value = '提交资源失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function normalizeStatus(status) {
  return status || 'PENDING';
}

function statusText(status) {
  if (status === 'ACTIVE') return '已上线';
  if (status === 'INACTIVE') return '已下线';
  return '待审核';
}

function statusTagType(status) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'INACTIVE') return 'default';
  return 'warning';
}

function levelText(level) {
  if (level === 'beginner') return '零基础';
  if (level === 'intermediate') return '有一点基础';
  if (level === 'advanced') return '进阶';
  return '不限';
}

async function reingestUrl(row) {
  error.value = '';
  success.value = '';
  try {
    await reingestResourceUrl(row.id, row.url, crypto.randomUUID());
    success.value = '资源重新摄取任务已提交。';
    await loadResources();
  } catch (e) {
    console.error(e);
    error.value = '重新摄取失败，请检查来源地址和任务服务。';
  }
}

function domainText(domain) {
  const value = String(domain || '').toLowerCase();
  if (value === 'java') return 'Java 后端';
  if (value === 'python') return 'Python';
  if (value === 'database') return '数据库 / SQL';
  if (value === 'english') return '英语';
  if (value === 'math') return '数学';
  if (value === 'frontend') return '前端';
  if (value === 'devops') return 'Linux / 运维';
  if (value === 'general') return '通用学习方法';
  return '未分类';
}

function levelTagType(level) {
  if (level === 'advanced') return 'warning';
  if (level === 'intermediate') return 'info';
  if (level === 'beginner') return 'success';
  return 'default';
}
async function changeStatus(item, status) {
  error.value = '';
  try {
    await updateResourceStatus(item.id, status);
    await loadResources();
    await loadQualityStats();
  } catch (e) {
    console.error(e);
    error.value = '更新资源状态失败，请稍后再试。';
  }
}

function formatDuration(totalMinutes) {
  if (!totalMinutes || totalMinutes <= 0) return '';
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  const parts = [];
  if (hours > 0) parts.push(`${hours} 小时`);
  if (minutes > 0) parts.push(`${minutes} 分钟`);
  return parts.join(' ');
}

function splitTags(tags) {
  if (!tags) return [];
  return String(tags).split(',').map((item) => item.trim()).filter(Boolean);
}

function getDomainFromUrl(url) {
  if (!url) return '';
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return '';
  }
}

function focusPending() {
  filterStatus.value = 'PENDING';
}

function focusRisky() {
  riskFilter.value = 'reported';
}

function clearSingleFilter(key) {
  if (key === 'keyword') filterKeyword.value = '';
  if (key === 'domain') filterDomain.value = '';
  if (key === 'level') filterLevel.value = '';
  if (key === 'status') filterStatus.value = '';
  if (key === 'risk') riskFilter.value = '';

  const nextQuery = { ...route.query };
  if (key === 'keyword') delete nextQuery.keyword;
  if (key === 'domain') delete nextQuery.domain;
  if (key === 'level') delete nextQuery.level;
  if (key === 'status') delete nextQuery.status;
  if (key === 'risk') delete nextQuery.risk;
  router.replace({ query: nextQuery });
}

function clearFilters() {
  filterKeyword.value = '';
  filterDomain.value = '';
  filterLevel.value = '';
  filterStatus.value = '';
  riskFilter.value = '';
  router.replace({ query: {} });
}

function openEdit(row) {
  editing.id = row.id;
  editing.title = row.title;
  editing.url = row.url;
  editing.domain = row.domain;
  editing.level = row.level;
  editing.durationMinutes = row.durationMinutes;
  editing.tags = row.tags;
  editModal.value = true;
}

async function saveEdit() {
  if (!editing.id) return;
  error.value = '';
  try {
    await updateResource(editing.id, { title: editing.title, url: editing.url, domain: editing.domain, level: editing.level, durationMinutes: editing.durationMinutes, tags: editing.tags });
    editModal.value = false;
    await loadResources();
    await loadQualityStats();
  } catch (e) {
    console.error(e);
    error.value = '保存编辑失败，请稍后再试';
  }
}

async function batchSet(status) {
  if (!checkedRowKeys.value.length) return;
  error.value = '';
  try {
    await batchUpdateResourceStatus(checkedRowKeys.value, status);
    checkedRowKeys.value = [];
    await loadResources();
    await loadQualityStats();
  } catch (e) {
    console.error(e);
    error.value = '批量操作失败，请稍后再试';
  }
}

function exportCsv() {
  const rows = filteredResources.value;
  if (!rows.length) return;
  const header = ['标题', 'URL', '领域', '水平', '时长(分钟)', '标签', '状态', '平均评分', '反馈数', '举报数'];
  const lines = rows.map((row) => [row.title ?? '', row.url ?? '', domainText(row.domain), row.level ?? '', row.durationMinutes ?? '', row.tags ?? '', statusText(normalizeStatus(row.status)), row.avgRating ?? '', row.feedbackCount ?? '', row.invalidReportCount ?? ''].map((value) => `"${String(value ?? '').replace(/"/g, '""')}"`).join(','));
  const csv = '\uFEFF' + [header.join(','), ...lines].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'resources.csv';
  anchor.click();
  URL.revokeObjectURL(url);
}
</script>

<style scoped>
.resource-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 4px;
}

.resource-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(280px, 0.7fr);
  gap: 16px;
  padding: 22px 24px;
  border-radius: 24px;
  border: 1px solid rgba(17, 42, 59, 0.08);
  background: radial-gradient(circle at top left, rgba(255, 255, 255, 0.82), transparent 35%), linear-gradient(135deg, #f8fcfc, #f0f6f3 55%, #eef3f7);
}

.page-kicker,
.card-kicker {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  color: #6f8191;
}

.page-title {
  margin: 6px 0 0;
  font-family: 'Georgia', 'Times New Roman', serif;
  font-size: 31px;
  line-height: 1.08;
  color: #102235;
}

.page-desc,
.subtitle,
.helper-text,
.hero-side-desc,
.summary-desc,
.toolbar-hint,
.empty-desc {
  font-size: 13px;
  line-height: 1.8;
  color: #607483;
}

.page-desc {
  margin: 10px 0 0;
  max-width: 860px;
}

.hero-meta,
.form-note-row {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-side-card {
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, #173850, #2b6661 125%);
  color: #f8fafc;
  box-shadow: 0 16px 28px rgba(15, 41, 64, 0.16);
}

.hero-side-label {
  font-size: 12px;
  color: rgba(243, 249, 251, 0.72);
}

.hero-side-title {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.25;
}

.hero-side-desc {
  margin-top: 8px;
  color: rgba(243, 249, 251, 0.84);
}

.hero-side-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  padding: 16px 18px;
  border-radius: 20px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: linear-gradient(180deg, #ffffff, #f7fbfb);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.summary-label,
.quality-label {
  font-size: 12px;
  color: #6b7280;
}

.summary-value,
.quality-value {
  margin-top: 4px;
  font-size: 28px;
  font-weight: 700;
  color: #102235;
}

.summary-desc {
  margin-top: 6px;
}

.resource-layout {
  display: grid;
  grid-template-columns: minmax(300px, 0.95fr) minmax(0, 1.65fr);
  gap: 16px;
  align-items: start;
}

.resource-form-card,
.resource-table-card {
  border-radius: 22px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}
.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.card-head-spread {
  align-items: center;
}

.card-title {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 700;
  color: #102235;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.form-full {
  grid-column: 1 / -1;
}

.form-actions {
  margin-top: 2px;
}

.duration-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.duration-input {
  max-width: 90px;
}

.duration-separator {
  font-size: 13px;
  color: #4b5563;
}

.form-alert {
  margin-top: 12px;
}

.table-head-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.quality-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.quality-stat-card {
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfcfd, #f6fafb);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.filter-panel {
  margin-top: 16px;
  padding: 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, #fbfcfd, #f7fafb);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.filter-row,
.toolbar-row,
.toolbar-actions,
.active-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.filter-row {
  align-items: center;
}

.active-filters {
  align-items: center;
  margin-top: 12px;
}

.active-filters-label {
  font-size: 12px;
  color: #5e7382;
}

.filter-chip-button,
.inline-tag-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(43, 102, 97, 0.16);
  background: #f3f7f7;
  color: #2b6661;
  font-size: 12px;
}

.filter-chip-button {
  cursor: pointer;
}

.toolbar-row {
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.toolbar-actions {
  justify-content: flex-end;
}

.field-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 9px;
  border-radius: 999px;
  background: #eff5f6;
  color: #45606f;
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.table-title-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.resource-link {
  color: #245d78;
  text-decoration: none;
  font-weight: 700;
}

.resource-link:hover {
  text-decoration: underline;
}

.table-title-meta,
.quality-subline {
  font-size: 12px;
  color: #6b7280;
}

.tag-stack,
.table-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.quality-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.quality-line {
  font-size: 13px;
  font-weight: 700;
  color: #173850;
}

.empty-state {
  margin-top: 14px;
  padding: 24px;
  border-radius: 18px;
  border: 1px dashed rgba(148, 163, 184, 0.4);
  background: #fbfcfd;
  text-align: center;
}

.empty-title {
  font-size: 16px;
  font-weight: 700;
  color: #102235;
}

.empty-actions {
  margin-top: 12px;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .resource-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .resource-hero,
  .quality-summary-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .card-head,
  .card-head-spread,
  .toolbar-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
