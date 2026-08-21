<template>
  <div class="resource-upload-page">
    <section class="upload-hero">
      <div class="upload-hero-copy">
        <div class="hero-kicker">Community Resource Intake</div>
        <h1 class="hero-title">把你觉得值得学的资源沉淀进 LearnFlow</h1>
        <p class="hero-subtitle">
          这里是学习端的资源提交入口。你可以把视频、文章、文档和项目教程提交到资源库，系统会先进入待审队列，由管理员审核后再决定是否参与推荐。
        </p>
        <div class="hero-chip-row">
          <span v-for="chip in heroChips" :key="chip" class="hero-chip">{{ chip }}</span>
        </div>
      </div>

      <div class="hero-side-panel">
        <div class="hero-panel-title">提交后会发生什么</div>
        <div class="hero-facts-grid">
          <div v-for="fact in heroFacts" :key="fact.label" class="hero-fact-card">
            <div class="hero-fact-label">{{ fact.label }}</div>
            <div class="hero-fact-value">{{ fact.value }}</div>
          </div>
        </div>
        <div class="hero-flow">
          <div v-for="step in reviewSteps" :key="step.title" class="hero-flow-item">
            <div class="hero-flow-index">{{ step.index }}</div>
            <div>
              <div class="hero-flow-title">{{ step.title }}</div>
              <div class="hero-flow-desc">{{ step.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <div class="upload-layout">
      <n-card class="resource-upload-card" size="small" :bordered="true">
        <template #header>
          <div class="panel-header">
            <div>
              <div class="panel-kicker">提交内容</div>
              <div class="panel-title">上传学习资源</div>
            </div>
            <div class="panel-badge">Pending Review</div>
          </div>
        </template>

        <p class="subtitle">
          支持网页链接、直接文本和 PDF/DOCX 文档。系统会先安全解析并切分，再进入审核队列。
        </p>

        <div class="field-chip-row">
          <span class="field-chip">title</span>
          <span class="field-chip">sourceType</span>
          <span class="field-chip">domain</span>
          <span class="field-chip">level</span>
          <span class="field-chip">durationMinutes</span>
          <span class="field-chip">tags</span>
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
            <n-form-item label="资源来源（必填）" path="sourceType" class="form-full">
              <n-select v-model:value="form.sourceType" :options="sourceTypeOptions" />
            </n-form-item>
            <n-form-item label="资源标题（必填）" path="title">
              <n-input
                v-model:value="form.title"
                placeholder="例如：Java 基础语法入门（B 站视频）"
              />
            </n-form-item>

            <n-form-item v-if="form.sourceType === 'URL'" label="资源链接 URL（必填）" path="url">
              <n-input
                v-model:value="form.url"
                placeholder="例如：https://www.bilibili.com/..."
              />
            </n-form-item>

            <n-form-item v-else-if="form.sourceType === 'TEXT'" label="资源正文（必填）" path="text" class="form-full">
              <n-input v-model:value="form.text" type="textarea" :autosize="{ minRows: 6, maxRows: 14 }" placeholder="粘贴有权摄取的学习文本" />
            </n-form-item>

            <n-form-item v-else label="PDF / DOCX / TXT 文档（必填）" path="document" class="form-full">
              <input class="document-input" type="file" accept=".pdf,.doc,.docx,.txt,.md,.rtf" @change="onDocumentSelected" />
              <span class="helper-text">单个文件最多 10 MB；不接受可执行文件或加密文档。</span>
            </n-form-item>

            <n-form-item label="适用水平" path="level">
              <n-select
                v-model:value="form.level"
                :options="levelOptions"
                placeholder="不限"
              />
            </n-form-item>

            <n-form-item label="资源领域（必填）" path="domain">
              <n-select
                v-model:value="form.domain"
                :options="domainOptions"
                placeholder="选择资源所属领域"
              />
            </n-form-item>

            <n-form-item label="预计学习时长" path="duration">
              <div class="duration-row">
                <n-input-number
                  v-model:value="form.durationHours"
                  :min="0"
                  size="small"
                  placeholder="小时"
                  class="duration-input"
                />
                <span class="duration-separator">小时</span>
                <n-input-number
                  v-model:value="form.durationMinutes"
                  :min="0"
                  size="small"
                  placeholder="分钟"
                  class="duration-input"
                />
                <span class="duration-separator">分钟</span>
              </div>
            </n-form-item>

            <n-form-item label="标签（逗号分隔）" path="tags" class="form-full">
              <n-input
                v-model:value="form.tags"
                placeholder="例如：java,basic,bilibili"
              />
            </n-form-item>

            <n-form-item path="rightsConfirmed" class="form-full">
              <n-checkbox v-model:checked="form.rightsConfirmed">
                我确认有权提交并允许 LearnFlow 解析、保存和建立索引；系统也会尊重来源站点的禁止索引信号。
              </n-checkbox>
            </n-form-item>
          </div>

          <div class="preview-card">
            <div class="preview-head">
              <div>
                <div class="preview-title">提交摘要</div>
                <div class="preview-caption">这些信息会直接进入待审队列</div>
              </div>
              <span class="preview-status">{{ resourceReadinessLabel }}</span>
            </div>
            <div class="preview-grid">
              <div v-for="fact in previewFacts" :key="fact.label" class="preview-item">
                <div class="preview-label">{{ fact.label }}</div>
                <div class="preview-value">{{ fact.value }}</div>
              </div>
            </div>
          </div>

          <div class="submit-row">
            <p class="helper-text submit-hint">
              提交成功后，资源会以 <b>PENDING</b> 状态写入资源库，需管理员在管理端审核后才会上线。
            </p>
            <div class="submit-actions">
              <n-button secondary @click="resetForm" :disabled="loading">清空内容</n-button>
              <n-button type="primary" attr-type="submit" :loading="loading">
                {{ loading ? '正在提交…' : '提交资源（待审核）' }}
              </n-button>
            </div>
          </div>
        </n-form>

        <n-alert
          v-if="error"
          type="error"
          closable
          class="form-alert"
          @close="error = ''"
        >
          {{ error }}
        </n-alert>
        <n-alert
          v-if="success"
          type="success"
          closable
          class="form-alert"
          @close="success = ''"
        >
          {{ success }}
        </n-alert>
      </n-card>

      <div class="upload-side-stack">
        <n-card class="guide-card" size="small" :bordered="true">
          <template #header>
            <div class="side-head">
              <div>
                <div class="panel-kicker">提交建议</div>
                <div class="side-title">什么资源更容易通过审核</div>
              </div>
            </div>
          </template>

          <div class="guide-list">
            <article v-for="tip in qualityChecklist" :key="tip.title" class="guide-item">
              <div class="guide-item-title">{{ tip.title }}</div>
              <div class="guide-item-desc">{{ tip.desc }}</div>
            </article>
          </div>
        </n-card>

        <n-card class="guide-card" size="small" :bordered="true">
          <template #header>
            <div class="side-head">
              <div>
                <div class="panel-kicker">审核流程</div>
                <div class="side-title">提交后的下一步</div>
              </div>
            </div>
          </template>

          <div class="status-timeline">
            <div v-for="step in reviewSteps" :key="step.index" class="status-item">
              <div class="status-index">{{ step.index }}</div>
              <div>
                <div class="status-title">{{ step.title }}</div>
                <div class="status-desc">{{ step.desc }}</div>
              </div>
            </div>
          </div>
        </n-card>

        <n-card v-if="lastSubmittedTitle" class="guide-card success-card" size="small" :bordered="true">
          <template #header>
            <div class="side-head">
              <div>
                <div class="panel-kicker">最近提交</div>
                <div class="side-title">你刚刚提交了一条新资源</div>
              </div>
            </div>
          </template>

          <div class="last-submit-title">{{ lastSubmittedTitle }}</div>
          <div class="status-desc">它现在会在管理端进入待审队列，通过后才会被后续计划推荐使用。</div>
        </n-card>
      </div>
    </div>

    <section ref="recordSectionRef" class="record-section">
      <div class="record-section-head">
        <div>
          <div class="panel-kicker">我的资源台账</div>
          <h2 class="record-title">我的上传记录 / 审核状态查询</h2>
          <p class="record-subtitle">在这里集中查看每一次提交、当前审核进度，以及最近一次状态更新时间。</p>
        </div>
        <div class="record-toolbar">
          <button
            v-for="filter in statusFilters"
            :key="filter.value"
            type="button"
            class="record-filter-chip"
            :class="{ active: statusFilter === filter.value }"
            @click="statusFilter = filter.value"
          >
            {{ filter.label }}
          </button>
          <n-button secondary size="small" @click="loadMyUploads" :loading="recordLoading">刷新记录</n-button>
        </div>
      </div>

      <div class="record-summary-grid">
        <article v-for="item in recordStats" :key="item.label" class="record-summary-card">
          <div class="record-summary-label">{{ item.label }}</div>
          <div class="record-summary-value">{{ item.value }}</div>
          <div class="record-summary-note">{{ item.note }}</div>
        </article>
      </div>

      <n-alert
        v-if="recordError"
        type="warning"
        closable
        class="record-alert"
        @close="recordError = ''"
      >
        {{ recordError }}
      </n-alert>

      <n-spin :show="recordLoading">
        <div v-if="filteredUploads.length" class="record-list">
          <article v-for="item in filteredUploads" :key="item.id" class="record-card">
            <div class="record-card-head">
              <div>
                <div class="record-card-title">{{ item.title }}</div>
                <div class="record-card-meta">
                  {{ item.uploaderUsername || currentUser?.username || '未知用户' }}
                  <span class="record-meta-dot">/</span>
                  {{ formatDateTime(item.createdAt) }}
                </div>
              </div>
              <n-tag size="small" :type="getStatusTone(item.status)">{{ getStatusLabel(item.status) }}</n-tag>
            </div>

            <p class="record-card-desc">{{ getStatusDescription(item.status) }}</p>

            <div class="record-fact-grid">
              <div class="record-fact-item">
                <div class="record-fact-label">资源领域</div>
                <div class="record-fact-value">{{ formatDomain(item.domain) }}</div>
              </div>
              <div class="record-fact-item">
                <div class="record-fact-label">适用水平</div>
                <div class="record-fact-value">{{ formatLevel(item.level) }}</div>
              </div>
              <div class="record-fact-item">
                <div class="record-fact-label">学习时长</div>
                <div class="record-fact-value">{{ formatDuration(item.durationMinutes) }}</div>
              </div>
              <div class="record-fact-item record-fact-item-wide">
                <div class="record-fact-label">资源标签</div>
                <div class="record-fact-value">{{ formatTags(item.tags) }}</div>
              </div>
              <div class="record-fact-item record-fact-item-wide">
                <div class="record-fact-label">最近更新</div>
                <div class="record-fact-value">{{ formatDateTime(item.updatedAt || item.createdAt) }}</div>
              </div>
            </div>

            <div class="record-card-footer">
              <a v-if="item.url" class="record-link" :href="item.url" target="_blank" rel="noreferrer">打开资源</a>
              <span v-else class="record-id">{{ item.sourceType || 'DOCUMENT' }} · {{ item.ingestionStatus || 'PENDING' }}</span>
              <span class="record-id">ID #{{ item.id }}</span>
            </div>
          </article>
        </div>

        <n-empty
          v-else
          :description="emptyRecordDescription"
          class="record-empty"
        />
      </n-spin>
    </section>
  </div>
</template>
<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { listMyResources, submitResourceDocument, submitResourceText, submitResourceUrl } from '../api/resource';
import { useAuthStore } from '../store/auth';

const { currentUser } = useAuthStore();

const form = reactive({
  sourceType: 'URL',
  title: '',
  url: '',
  text: '',
  document: null,
  rightsConfirmed: false,
  domain: '',
  level: '',
  durationHours: null,
  durationMinutes: null,
  tags: ''
});

const sourceTypeOptions = [
  { label: '网页链接', value: 'URL' },
  { label: '直接文本', value: 'TEXT' },
  { label: 'PDF / Word / 文本文件', value: 'DOCUMENT' }
];

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

const heroChips = ['待审提交', '社区资源', 'RAG 可用性', '管理端审核', '标签匹配'];

const qualityChecklist = [
  {
    title: '标题尽量具体',
    desc: '用“主题 + 形式 + 来源”的方式命名，比笼统的“Java 教程”更容易通过审核。'
  },
  {
    title: '链接要可直接访问',
    desc: '尽量提交正常可打开的资源页，避免需要额外授权或已失效的 URL。'
  },
  {
    title: '标签有助于推荐',
    desc: '标签里可以包含主题、难度、形式或平台，比如 java,backend,video,beginner。'
  }
];

const reviewSteps = [
  { index: '01', title: '提交入库', desc: '前端把资源写入资源库，默认状态为 PENDING。' },
  { index: '02', title: '管理端审核', desc: '管理员会在“资源管理”页查看、筛选和审核这条资源。' },
  { index: '03', title: '通过后上线', desc: '审核通过的资源才会参与后续计划推荐和资源匹配。' }
];

const statusFilters = [
  { value: 'all', label: '全部' },
  { value: 'PENDING', label: '待审核' },
  { value: 'ACTIVE', label: '已通过' },
  { value: 'INACTIVE', label: '已下线' }
];

const rules = {
  title: {
    required: true,
    message: '请填写资源标题',
    trigger: ['input', 'blur']
  },
  url: {
    validator(_rule, value) {
      if (form.sourceType !== 'URL') return true;
      const text = String(value || '').trim();
      if (!text) return new Error('请填写资源链接');
      if (!/^https?:\/\//i.test(text)) {
        return new Error('链接需以 http:// 或 https:// 开头');
      }
      return true;
    },
    trigger: ['input', 'blur']
  },
  text: {
    validator() {
      if (form.sourceType === 'TEXT' && !String(form.text || '').trim()) return new Error('请填写资源正文');
      return true;
    },
    trigger: ['input', 'blur']
  },
  document: {
    validator() {
      if (form.sourceType === 'DOCUMENT' && !form.document) return new Error('请选择文档');
      return true;
    },
    trigger: ['change']
  },
  rightsConfirmed: {
    validator() {
      return form.rightsConfirmed ? true : new Error('请先确认内容处理权限');
    },
    trigger: ['change']
  },
  domain: {
    required: true,
    message: '请选择资源领域',
    trigger: ['change', 'blur']
  }
};

const formRef = ref(null);
const loading = ref(false);
const error = ref('');
const success = ref('');
const lastSubmittedTitle = ref('');
const myUploads = ref([]);
const recordLoading = ref(false);
const recordError = ref('');
const statusFilter = ref('all');
const recordSectionRef = ref(null);

const normalizedTags = computed(() =>
  String(form.tags || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
);

const totalMinutes = computed(() =>
  Number(form.durationHours || 0) * 60 + Number(form.durationMinutes || 0)
);

const durationLabel = computed(() => {
  if (!totalMinutes.value) return '未填写';
  const hours = Math.floor(totalMinutes.value / 60);
  const minutes = totalMinutes.value % 60;
  const parts = [];
  if (hours > 0) parts.push(`${hours} 小时`);
  if (minutes > 0) parts.push(`${minutes} 分钟`);
  return parts.join(' ');
});

const selectedLevelLabel = computed(() =>
  levelOptions.find((item) => item.value === form.level)?.label || '不限'
);

const selectedDomainLabel = computed(() =>
  domainOptions.find((item) => item.value === form.domain)?.label || '未选择'
);

const resourceReadinessLabel = computed(() => {
  const sourceReady = form.sourceType === 'URL' ? form.url : form.sourceType === 'TEXT' ? form.text : form.document;
  const score = [form.title, sourceReady, form.domain, form.level || 'x', normalizedTags.value.length ? 'x' : '', totalMinutes.value ? 'x' : '']
    .filter(Boolean)
    .length;
  if (score >= 5) return '信息完整';
  if (score >= 3) return '可以提交';
  return '建议补充';
});

const previewFacts = computed(() => [
  { label: '标题状态', value: form.title ? '已填写' : '待补充' },
  { label: '来源类型', value: sourceTypeOptions.find((item) => item.value === form.sourceType)?.label || '未选择' },
  { label: '资源领域', value: selectedDomainLabel.value },
  { label: '适用水平', value: selectedLevelLabel.value },
  { label: '预计时长', value: durationLabel.value },
  { label: '标签数量', value: normalizedTags.value.length ? `${normalizedTags.value.length} 个` : '未填写' },
  { label: '提交状态', value: resourceReadinessLabel.value }
]);

const heroFacts = computed(() => [
  { label: '当前提交状态', value: resourceReadinessLabel.value },
  { label: '当前领域', value: selectedDomainLabel.value },
  { label: '标签数量', value: normalizedTags.value.length ? `${normalizedTags.value.length} 个` : '未填写' },
  { label: '待审记录', value: String(myUploads.value.filter((item) => item.status === 'PENDING').length) },
  { label: '我的提交总数', value: String(myUploads.value.length) }
]);

const filteredUploads = computed(() => {
  if (statusFilter.value === 'all') return myUploads.value;
  return myUploads.value.filter((item) => String(item.status || '').toUpperCase() === statusFilter.value);
});

const recordStats = computed(() => {
  const pending = myUploads.value.filter((item) => item.status === 'PENDING').length;
  const active = myUploads.value.filter((item) => item.status === 'ACTIVE').length;
  const inactive = myUploads.value.filter((item) => item.status === 'INACTIVE').length;
  return [
    { label: '累计上传', value: String(myUploads.value.length), note: '已进入资源库的全部提交记录。' },
    { label: '待审核', value: String(pending), note: '正在等待管理端处理的资源。' },
    { label: '已通过', value: String(active), note: '已可参与后续推荐与资源匹配。' },
    { label: '已下线', value: String(inactive), note: '未通过审核或后续被下线的资源。' }
  ];
});

const emptyRecordDescription = computed(() => {
  if (statusFilter.value === 'PENDING') return '当前没有待审核的上传记录。';
  if (statusFilter.value === 'ACTIVE') return '当前没有已通过审核的资源。';
  if (statusFilter.value === 'INACTIVE') return '当前没有已下线或被驳回的资源。';
  return '你还没有上传过资源，可以先提交一条试试。';
});

function getStatusTone(status) {
  const normalized = String(status || 'PENDING').toUpperCase();
  if (normalized === 'ACTIVE') return 'success';
  if (normalized === 'INACTIVE') return 'warning';
  return 'info';
}

function getStatusLabel(status) {
  const normalized = String(status || 'PENDING').toUpperCase();
  if (normalized === 'ACTIVE') return '已通过';
  if (normalized === 'INACTIVE') return '已下线';
  return '待审核';
}

function getStatusDescription(status) {
  const normalized = String(status || 'PENDING').toUpperCase();
  if (normalized === 'ACTIVE') return '资源已通过管理端审核，可以参与后续推荐。';
  if (normalized === 'INACTIVE') return '资源当前未通过审核或已下线，你可以优化后再次提交。';
  return '资源已成功提交，正在等待管理端审核。';
}

function formatLevel(level) {
  if (level === 'beginner') return '零基础';
  if (level === 'intermediate') return '有一点基础';
  if (level === 'advanced') return '进阶';
  return '不限';
}

function formatDomain(domain) {
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

function formatDuration(minutes) {
  const value = Number(minutes || 0);
  if (!value) return '未填写';
  const hours = Math.floor(value / 60);
  const rest = value % 60;
  const parts = [];
  if (hours > 0) parts.push(`${hours} 小时`);
  if (rest > 0) parts.push(`${rest} 分钟`);
  return parts.join(' ');
}

function formatTags(tags) {
  if (!tags) return '暂无标签';
  return String(tags)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .join(' / ') || '暂无标签';
}

function formatDateTime(value) {
  if (!value) return '刚刚';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
}

async function loadMyUploads() {
  const userId = currentUser.value?.id;
  const username = currentUser.value?.username;
  if (!userId && !username) {
    myUploads.value = [];
    recordError.value = '当前用户信息不完整，暂时无法查询上传记录。';
    return;
  }

  recordLoading.value = true;
  recordError.value = '';
  try {
    const list = await listMyResources({ userId, username });
    myUploads.value = Array.isArray(list) ? list : [];
  } catch (e) {
    console.error(e);
    recordError.value = '加载上传记录失败，请稍后再试。';
  } finally {
    recordLoading.value = false;
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
    const cleanedTags = normalizedTags.value.join(',');

    const payload = {
      title: form.title,
      domain: form.domain,
      level: form.level || null,
      durationMinutes: totalMinutes.value || null,
      tags: cleanedTags || null,
      rightsConfirmed: form.rightsConfirmed
    };
    const idempotencyKey = crypto.randomUUID();
    if (form.sourceType === 'URL') {
      await submitResourceUrl({ ...payload, url: form.url }, idempotencyKey);
    } else if (form.sourceType === 'TEXT') {
      await submitResourceText({ ...payload, text: form.text }, idempotencyKey);
    } else {
      await submitResourceDocument(payload, form.document, idempotencyKey);
    }

    lastSubmittedTitle.value = form.title;
    success.value = '资源已进入安全摄取任务，解析完成后等待管理员审核。';
    resetForm();
    await loadMyUploads();
    await nextTick();
    scrollToRecords();
  } catch (e) {
    console.error(e);
    error.value = '提交资源失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  form.sourceType = 'URL';
  form.title = '';
  form.url = '';
  form.text = '';
  form.document = null;
  form.rightsConfirmed = false;
  form.domain = '';
  form.level = '';
  form.durationHours = null;
  form.durationMinutes = null;
  form.tags = '';
}

function onDocumentSelected(event) {
  form.document = event.target.files?.[0] || null;
}

function scrollToRecords() {
  recordSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

watch(
  () => `${currentUser.value?.id || ''}:${currentUser.value?.username || ''}`,
  () => {
    loadMyUploads();
  }
);

onMounted(() => {
  loadMyUploads();
});
</script>

<style scoped>
.resource-upload-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.upload-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.95fr);
  gap: 18px;
  padding: 24px 26px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.82), transparent 34%),
    linear-gradient(135deg, #173146, #25566a 46%, #d8efe7 180%);
  color: #f8fafc;
  box-shadow: 0 22px 48px rgba(12, 37, 53, 0.16);
}

.hero-kicker,
.panel-kicker {
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(232, 244, 248, 0.72);
}

.hero-title {
  margin: 8px 0 10px;
  font-size: clamp(30px, 3vw, 40px);
  line-height: 1.08;
  color: #f8fafc;
}

.hero-subtitle {
  margin: 0;
  max-width: 760px;
  font-size: 14px;
  line-height: 1.75;
  color: rgba(236, 245, 248, 0.9);
}

.hero-chip-row,
.field-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.hero-chip,
.field-chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
}

.hero-chip {
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: #f7fafc;
}

.field-chip {
  background: #eef5f6;
  color: #446070;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.hero-side-panel {
  padding: 18px;
  border-radius: 22px;
  background: rgba(9, 24, 33, 0.24);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10px);
}

.hero-panel-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 12px;
}

.hero-facts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.hero-fact-card {
  padding: 12px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.08);
}

.hero-fact-label {
  font-size: 12px;
  color: rgba(236, 245, 248, 0.72);
}

.hero-fact-value {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
}

.hero-flow,
.status-timeline,
.guide-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hero-flow-item,
.status-item {
  display: grid;
  grid-template-columns: 40px 1fr;
  gap: 10px;
  align-items: flex-start;
}

.hero-flow-index,
.status-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.1);
  font-size: 12px;
  font-weight: 700;
}

.hero-flow-title,
.status-title,
.guide-item-title,
.side-title,
.preview-title {
  font-size: 14px;
  font-weight: 700;
}

.hero-flow-desc,
.status-desc,
.guide-item-desc,
.subtitle,
.helper-text,
.preview-caption,
.last-submit-title {
  font-size: 13px;
  line-height: 1.7;
}

.hero-flow-desc {
  margin-top: 2px;
  color: rgba(236, 245, 248, 0.74);
}

.upload-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(300px, 0.88fr);
  gap: 16px;
}

.resource-upload-card,
.guide-card {
  align-self: flex-start;
  border-radius: 24px;
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.07);
}

.panel-header,
.side-head,
.preview-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.panel-title,
.side-title {
  margin-top: 4px;
  font-size: 22px;
  color: #102235;
}

.panel-badge,
.preview-status {
  display: inline-flex;
  align-items: center;
  padding: 7px 12px;
  border-radius: 999px;
  background: #edf7f2;
  color: #146c43;
  font-size: 12px;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  color: #667a8a;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.form-full {
  grid-column: 1 / -1;
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

.preview-card {
  margin-top: 16px;
  padding: 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f9fbfb, #f2f7f5);
  border: 1px solid rgba(19, 84, 90, 0.1);
}

.preview-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.preview-item {
  padding: 12px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid rgba(20, 51, 73, 0.08);
}

.preview-label {
  font-size: 12px;
  color: #738496;
}

.preview-value {
  margin-top: 4px;
  font-size: 16px;
  font-weight: 700;
  color: #102235;
}

.submit-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.submit-hint {
  margin: 0;
  color: #667a8a;
}

.submit-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.form-alert {
  margin-top: 10px;
}

.upload-side-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.guide-item {
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfcfd, #f7fafb);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.guide-item-desc,
.status-desc {
  margin-top: 4px;
  color: #667a8a;
}

.success-card {
  background: linear-gradient(180deg, #fcfffd, #f3faf5);
}

.last-submit-title {
  font-weight: 700;
  color: #173850;
}

.record-section {
  width: 100%;
  padding: 22px;
  border-radius: 24px;
  background:
    radial-gradient(circle at top right, rgba(216, 239, 231, 0.55), transparent 28%),
    linear-gradient(180deg, #ffffff, #f8fbfb);
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.07);
}

.record-section-head,
.record-card-head,
.record-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.record-title {
  margin: 6px 0 8px;
  font-size: 26px;
  color: #102235;
}

.record-subtitle {
  margin: 0;
  color: #667a8a;
}

.record-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.record-filter-chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(19, 84, 90, 0.12);
  background: #f6faf9;
  color: #516777;
  cursor: pointer;
  transition: all 0.2s ease;
}

.record-filter-chip.active,
.record-filter-chip:hover {
  background: #173850;
  border-color: #173850;
  color: #ffffff;
}

.record-summary-grid,
.record-fact-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.record-summary-card,
.record-fact-item {
  padding: 12px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid rgba(20, 51, 73, 0.08);
}

.record-summary-label,
.record-fact-label {
  font-size: 12px;
  color: #738496;
}

.record-summary-value,
.record-fact-value {
  margin-top: 4px;
  font-size: 16px;
  font-weight: 700;
  color: #102235;
}

.record-summary-note,
.record-card-meta,
.record-card-desc,
.record-id {
  margin-top: 4px;
  font-size: 12px;
  color: #667a8a;
  line-height: 1.6;
}

.record-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 14px;
}

.record-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(20, 51, 73, 0.08);
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.05);
}

.record-card-title {
  font-size: 17px;
  font-weight: 700;
  color: #102235;
}

.record-meta-dot {
  padding: 0 6px;
  color: #9aaab8;
}

.record-fact-item-wide {
  grid-column: span 2;
}

.record-link {
  color: #156f62;
  text-decoration: none;
  font-weight: 700;
}

.record-link:hover {
  text-decoration: underline;
}

.record-empty {
  padding: 20px 0 6px;
}

.record-alert {
  margin-top: 10px;
}

@media (max-width: 1100px) {
  .upload-hero,
  .upload-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 820px) {
  .preview-grid,
  .hero-facts-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .submit-row,
  .panel-header,
  .side-head,
  .preview-head {
    flex-direction: column;
    align-items: stretch;
  }

  .submit-actions {
    width: 100%;
  }

  .submit-actions :deep(.n-button) {
    flex: 1;
  }

  .upload-hero {
    padding: 20px 18px;
    border-radius: 22px;
  }
}
</style>
