<template>
  <div class="exercise-review-page">
    <div class="review-hero">
      <div>
        <div class="review-kicker">练习复盘台</div>
        <h1 class="title review-title">练习回顾</h1>
        <p class="subtitle review-subtitle">
          这里会沉淀每次练习的作答、AI 评分与反馈，你可以按计划、按练习日、按待复习状态快速回看自己的薄弱点和进步轨迹。
        </p>
      </div>

      <div class="review-focus-card">
        <div class="review-focus-label">当前回顾焦点</div>
        <div class="review-focus-title">{{ focusTitle }}</div>
        <div class="review-focus-meta">{{ focusMeta }}</div>
        <n-button secondary @click="loadRecords" :loading="loading" class="review-refresh-btn">
          {{ loading ? '正在刷新…' : '刷新记录' }}
        </n-button>
      </div>
    </div>

    <n-card size="small" class="filter-card review-panel-card">
      <template #header>
        <div class="panel-header">
          <div>
            <div class="panel-kicker">筛选视图</div>
            <div class="panel-title">快速定位要复盘的练习</div>
          </div>
        </div>
      </template>

      <div class="filter-grid">
        <div class="filter-item">
          <div class="filter-label">计划筛选</div>
          <n-select
            v-model:value="selectedPlanId"
            :options="planOptions"
            placeholder="查看全部计划"
            clearable
            @update:value="loadRecords"
          />
        </div>
        <div class="filter-item">
          <div class="filter-label">记录数量</div>
          <n-select
            v-model:value="limit"
            :options="limitOptions"
            @update:value="loadRecords"
          />
        </div>
        <div class="filter-item switch-item">
          <div class="filter-label">仅看待复习</div>
          <n-switch v-model:value="onlyNeedsReview" />
          <div class="switch-caption">优先聚焦低分、概念缺口和应用不稳的题目</div>
        </div>
      </div>
    </n-card>

    <div v-if="error" class="error-text page-error">{{ error }}</div>

    <div class="summary-grid">
      <n-card size="small" class="summary-card review-panel-card">
        <div class="summary-label">练习总数</div>
        <div class="summary-value">{{ summary.totalRecords }}</div>
        <div class="summary-desc">已评分 {{ summary.scoredRecords }} 条</div>
      </n-card>
      <n-card size="small" class="summary-card review-panel-card">
        <div class="summary-label">平均得分</div>
        <div class="summary-value">{{ summary.averageScore ?? '--' }}</div>
        <div class="summary-desc">最高分 {{ summary.highestScore ?? '--' }}</div>
      </n-card>
      <n-card size="small" class="summary-card review-panel-card">
        <div class="summary-label">掌握较稳</div>
        <div class="summary-value">{{ summary.masteredCount }}</div>
        <div class="summary-desc">最近得分 {{ summary.latestScore ?? '--' }}</div>
      </n-card>
      <n-card size="small" class="summary-card review-panel-card danger-card">
        <div class="summary-label">待重点复习</div>
        <div class="summary-value">{{ summary.needsReviewCount }}</div>
        <div class="summary-desc">建议优先回看低分与概念缺口题目</div>
      </n-card>
    </div>

    <n-card size="small" class="review-list-card review-panel-card">
      <template #header>
        <div class="list-header">
          <div>
            <div class="panel-kicker">记录分组</div>
            <div class="list-title">练习记录</div>
            <div class="list-subtitle">按计划天次聚合，方便回看某一天学了什么、错在什么、下一步练什么。</div>
          </div>
        </div>
      </template>

      <div v-if="loading" class="helper-text">正在加载练习记录...</div>
      <div v-else-if="groupedRecords.length === 0" class="empty-review-state">
        <div class="empty-review-code">02</div>
        <div class="empty-review-title">当前还没有可展示的练习记录</div>
        <div class="empty-review-desc">
          先回到学习计划页做几道题，之后这里就会形成一份真正可复盘的练习轨迹。
        </div>
      </div>

      <div v-else class="group-list">
        <section
          v-for="group in groupedRecords"
          :key="group.key"
          class="record-group"
        >
          <div class="group-header">
            <div>
              <div class="group-title">{{ group.dayTitle || '未命名练习日' }}</div>
              <div class="group-meta">
                <span>{{ group.planTitle || '未命名计划' }}</span>
                <span v-if="group.dayDate">{{ group.dayDate }}</span>
                <span>{{ group.records.length }} 条记录</span>
              </div>
            </div>
            <div class="group-actions">
              <div class="group-badge">{{ group.records.filter((record) => isNeedsReview(record)).length }} 条待复盘</div>
              <n-button
                v-if="group.dayId"
                size="tiny"
                quaternary
                type="error"
                :loading="deletingDayIds[group.dayId]"
                @click="handleDeleteDayRecords(group)"
              >
                {{ deletingDayIds[group.dayId] ? '正在清空…' : '清空本日记录' }}
              </n-button>
            </div>
          </div>

          <div class="record-list">
            <article
              v-for="record in group.records"
              :key="record.id"
              class="record-item"
            >
              <div class="record-top-row">
                <div class="record-question">{{ record.question }}</div>
                <div class="record-side-actions">
                  <div class="record-score-block">
                    <n-tag
                      v-if="record.aiScore != null"
                      size="small"
                      :type="scoreTagType(record.aiScore)"
                    >
                      得分 {{ record.aiScore }}
                    </n-tag>
                    <span
                      v-if="record.aiMistakeType"
                      class="mistake-type"
                    >
                      {{ formatMistakeType(record.aiMistakeType) }}
                    </span>
                  </div>
                  <n-button
                    size="tiny"
                    quaternary
                    type="error"
                    :loading="deletingRecordIds[record.id]"
                    @click="handleDeleteRecord(record)"
                  >
                    {{ deletingRecordIds[record.id] ? '删除中…' : '删除' }}
                  </n-button>
                </div>
              </div>

              <div v-if="record.difficulty || record.skillFocus || record.createdAt" class="record-meta-tags">
                <span v-if="record.difficulty">难度：{{ record.difficulty }}</span>
                <span v-if="record.skillFocus">考察点：{{ record.skillFocus }}</span>
                <span v-if="record.createdAt">提交时间：{{ formatDateTime(record.createdAt) }}</span>
              </div>

              <div class="answer-block user-answer-block">
                <div class="answer-label">你的答案</div>
                <div class="answer-content">{{ record.userAnswer || '暂无作答内容' }}</div>
              </div>

              <div v-if="record.aiFeedback || record.aiNextRecommendation" class="feedback-panel">
                <div v-if="record.aiFeedback" class="feedback-text">
                  <strong>AI 反馈：</strong>{{ record.aiFeedback }}
                </div>
                <div v-if="record.aiNextRecommendation" class="feedback-next">
                  <strong>下一步建议：</strong>{{ record.aiNextRecommendation }}
                </div>
              </div>

              <details class="record-details">
                <summary>查看参考答案与题目讲解</summary>
                <div class="answer-block">
                  <div class="answer-label">参考答案</div>
                  <div class="answer-content">{{ record.referenceAnswer || '暂无参考答案' }}</div>
                </div>
                <div v-if="record.explanation" class="answer-block">
                  <div class="answer-label">题目讲解</div>
                  <div class="answer-content">{{ record.explanation }}</div>
                </div>
              </details>
            </article>
          </div>
        </section>
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import {
  deleteExerciseRecord,
  deleteExerciseRecordsByDay,
  getExerciseRecords,
  getRecentPlans
} from '../api/plan';
import { useAuthStore } from '../store/auth';
import { scoreTagType, formatMistakeType, isNeedsReview } from '../utils/exercise';

const { currentUser } = useAuthStore();

const loading = ref(false);
const error = ref('');
const rawRecords = ref([]);
const selectedPlanId = ref(null);
const onlyNeedsReview = ref(false);
const limit = ref(50);
const plans = ref([]);
const deletingRecordIds = ref({});
const deletingDayIds = ref({});

const limitOptions = [
  { label: '最近 20 条', value: 20 },
  { label: '最近 50 条', value: 50 },
  { label: '最近 100 条', value: 100 }
];

const planOptions = computed(() => [
  { label: '全部计划', value: null },
  ...plans.value.map((plan) => ({
    label: plan.title || `学习计划 #${plan.id}`,
    value: plan.id
  }))
]);

const visibleRecords = computed(() => {
  if (!onlyNeedsReview.value) {
    return rawRecords.value;
  }
  return rawRecords.value.filter((record) => isNeedsReview(record));
});

const summary = computed(() => {
  const items = visibleRecords.value;
  const scores = items
    .map((item) => item.aiScore)
    .filter((score) => score != null);

  return {
    totalRecords: items.length,
    scoredRecords: scores.length,
    averageScore: scores.length
      ? Math.round((scores.reduce((sum, score) => sum + score, 0) / scores.length) * 10) / 10
      : null,
    highestScore: scores.length ? Math.max(...scores) : null,
    latestScore: scores.length ? scores[0] : null,
    masteredCount: items.filter((item) => item.aiScore != null && item.aiScore >= 85).length,
    needsReviewCount: items.filter((item) => isNeedsReview(item)).length
  };
});

const groupedRecords = computed(() => {
  const groups = [];
  const map = new Map();

  visibleRecords.value.forEach((record) => {
    const key = `${record.planId || 'plan'}-${record.dayId || 'day'}`;
    if (!map.has(key)) {
      const group = {
        key,
        dayId: record.dayId,
        planTitle: record.planTitle,
        dayTitle: record.dayTitle,
        dayDate: record.dayDate,
        records: []
      };
      map.set(key, group);
      groups.push(group);
    }
    map.get(key).records.push(record);
  });

  return groups;
});

const focusTitle = computed(() => {
  if (onlyNeedsReview.value) {
    return '待复习问题集';
  }
  if (selectedPlanId.value) {
    const currentPlan = plans.value.find((item) => String(item.id) === String(selectedPlanId.value));
    return currentPlan?.title || '指定计划回顾';
  }
  return '全部练习记录';
});

const focusMeta = computed(() => {
  if (loading.value) {
    return '正在同步练习记录与 AI 评测结果…';
  }
  return `当前共 ${summary.value.totalRecords} 条记录，其中 ${summary.value.needsReviewCount} 条建议优先复盘。`;
});

onMounted(async () => {
  await Promise.all([loadPlans(), loadRecords()]);
});

async function loadPlans() {
  const userId = currentUser.value?.id;
  if (!userId) return;

  try {
    plans.value = await getRecentPlans(50, userId);
  } catch (e) {
    console.error('load review plans failed', e);
  }
}

async function loadRecords() {
  const userId = currentUser.value?.id;
  if (!userId) {
    error.value = '当前未检测到登录用户，无法加载练习记录。';
    rawRecords.value = [];
    return;
  }

  loading.value = true;
  error.value = '';

  try {
    const response = await getExerciseRecords({
      userId,
      planId: selectedPlanId.value,
      limit: limit.value
    });
    rawRecords.value = response?.items || [];
  } catch (e) {
    console.error(e);
    error.value = '加载练习记录失败，请稍后重试。';
    rawRecords.value = [];
  } finally {
    loading.value = false;
  }
}

function formatDateTime(value) {
  if (!value) return '';
  return String(value).replace('T', ' ');
}

async function handleDeleteRecord(record) {
  const userId = currentUser.value?.id;
  if (!userId || !record?.id) return;

  const confirmed = window.confirm('确认删除这条练习记录吗？删除后将无法恢复。');
  if (!confirmed) return;

  deletingRecordIds.value = {
    ...deletingRecordIds.value,
    [record.id]: true
  };

  try {
    await deleteExerciseRecord(record.id, userId);
    rawRecords.value = rawRecords.value.filter((item) => item.id !== record.id);
  } catch (e) {
    console.error(e);
    window.alert('删除练习记录失败，请稍后重试。');
  } finally {
    deletingRecordIds.value = {
      ...deletingRecordIds.value,
      [record.id]: false
    };
  }
}

async function handleDeleteDayRecords(group) {
  const userId = currentUser.value?.id;
  if (!userId || !group?.dayId) return;

  const confirmed = window.confirm(`确认清空“${group.dayTitle || '当前日程'}”下的全部练习记录吗？此操作无法恢复。`);
  if (!confirmed) return;

  deletingDayIds.value = {
    ...deletingDayIds.value,
    [group.dayId]: true
  };

  try {
    await deleteExerciseRecordsByDay(group.dayId, userId);
    rawRecords.value = rawRecords.value.filter((item) => String(item.dayId) !== String(group.dayId));
  } catch (e) {
    console.error(e);
    window.alert('清空当日练习记录失败，请稍后重试。');
  } finally {
    deletingDayIds.value = {
      ...deletingDayIds.value,
      [group.dayId]: false
    };
  }
}
</script>

<style scoped>
.exercise-review-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.review-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.75fr);
  gap: 16px;
  align-items: stretch;
  padding: 22px 24px;
  border-radius: 24px;
  border: 1px solid rgba(17, 42, 59, 0.08);
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.82), transparent 35%),
    linear-gradient(135deg, #f9fcfc, #f1f6f4 55%, #eef3f8);
}

.review-kicker,
.panel-kicker {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  color: #6f8191;
}

.review-title {
  margin-top: 6px;
  color: #102235;
}

.review-subtitle {
  margin-bottom: 0;
  max-width: 760px;
  line-height: 1.75;
}

.review-focus-card {
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, #173850, #2b6661 125%);
  color: #f8fafc;
  box-shadow: 0 16px 28px rgba(15, 41, 64, 0.16);
}

.review-focus-label {
  font-size: 12px;
  color: rgba(243, 249, 251, 0.72);
}

.review-focus-title {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.25;
}

.review-focus-meta {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: rgba(243, 249, 251, 0.84);
}

.review-refresh-btn {
  margin-top: 14px;
}

.review-panel-card {
  border-radius: 20px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-title,
.list-title {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
  color: #102235;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.switch-item {
  justify-content: center;
}

.filter-label {
  font-size: 12px;
  color: #6b7280;
}

.switch-caption {
  font-size: 12px;
  line-height: 1.6;
  color: #7a8b98;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  padding: 6px 8px;
  background: linear-gradient(180deg, #ffffff, #f7fbfb);
}

.danger-card {
  background: linear-gradient(180deg, rgba(254, 242, 242, 0.95), rgba(255, 255, 255, 1));
}

.summary-label {
  font-size: 12px;
  color: #6b7280;
}

.summary-value {
  margin-top: 4px;
  font-size: 26px;
  font-weight: 700;
  color: #102235;
}

.summary-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #7c8b98;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list-subtitle {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.record-group {
  padding: 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  border: 1px solid rgba(148, 163, 184, 0.24);
}

.group-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px dashed rgba(148, 163, 184, 0.4);
}

.group-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.group-title {
  font-size: 17px;
  font-weight: 700;
  color: #102235;
}

.group-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}

.group-badge {
  display: inline-flex;
  align-items: center;
  padding: 7px 12px;
  border-radius: 999px;
  background: #f4efe7;
  color: #9a5b12;
  font-size: 12px;
  font-weight: 700;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-item {
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfcfd, #f7fafb);
  border: 1px solid rgba(148, 163, 184, 0.22);
}

.record-top-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.record-side-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.record-question {
  font-size: 15px;
  font-weight: 700;
  color: #102235;
  line-height: 1.7;
}

.record-score-block {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.mistake-type {
  font-size: 12px;
  color: #b45309;
  font-weight: 600;
}

.record-meta-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
  font-size: 12px;
  color: #6b7280;
}

.answer-block {
  margin-top: 12px;
}

.answer-label {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}

.answer-content {
  white-space: pre-wrap;
  line-height: 1.75;
  color: #1f2937;
  font-size: 13px;
}

.user-answer-block .answer-content {
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.25);
}

.feedback-panel {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #eff6ff;
  border: 1px solid rgba(96, 165, 250, 0.24);
}

.feedback-text,
.feedback-next {
  font-size: 13px;
  color: #1f2937;
  line-height: 1.75;
  white-space: pre-wrap;
}

.feedback-next {
  margin-top: 6px;
}

.record-details {
  margin-top: 12px;
  font-size: 12px;
}

.page-error {
  margin-top: -2px;
}

.empty-review-state {
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px 16px;
  text-align: center;
}

.empty-review-code {
  width: 68px;
  height: 68px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 22px;
  background: linear-gradient(135deg, #173850, #2b6661);
  color: #ffffff;
  font-size: 24px;
  font-weight: 800;
}

.empty-review-title {
  font-size: 18px;
  font-weight: 700;
  color: #102235;
}

.empty-review-desc {
  max-width: 420px;
  font-size: 13px;
  line-height: 1.8;
  color: #6b7280;
}

@media (max-width: 960px) {
  .review-hero,
  .filter-grid,
  .summary-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .group-header,
  .record-top-row,
  .panel-header {
    flex-direction: column;
  }

  .record-score-block {
    flex-wrap: wrap;
  }
}
</style>
