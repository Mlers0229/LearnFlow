<template>
  <main class="resource-page">
    <header class="page-header">
      <div>
        <div class="eyebrow">ADMIN · RESOURCE OPERATIONS</div>
        <h1>资源管理</h1>
        <p>审核资源质量、处理举报并控制知识库上线范围。高风险和待审资源已自动排在队列前方。</p>
      </div>
      <div class="header-actions">
        <button class="ghost-button" type="button" @click="focusRisk">查看高风险</button>
        <button class="primary-button" type="button" @click="createOpen = !createOpen">{{ createOpen ? '收起录入' : '录入新资源' }}</button>
      </div>
    </header>

    <section class="summary-grid" aria-label="资源运营概览">
      <article><span>全部资源</span><strong>{{ enrichedResources.length }}</strong><small>可管理资源总量</small></article>
      <article class="pending"><span>等待审核</span><strong>{{ summary.pending }}</strong><small>需要管理员决策</small></article>
      <article class="risky"><span>高风险</span><strong>{{ summary.risky }}</strong><small>{{ summary.reports }} 次无效举报</small></article>
      <article><span>平均质量</span><strong>{{ summary.average }}</strong><small>{{ summary.rated }} 条有评分</small></article>
    </section>

    <Transition name="reveal">
      <section v-if="createOpen" class="create-panel">
        <div class="panel-heading"><div><span class="eyebrow">QUICK INTAKE</span><h2>录入学习资源</h2></div><small>提交后默认进入待审核队列</small></div>
        <form class="create-form" @submit.prevent="submitResource">
          <label class="wide">资源标题<input v-model.trim="createForm.title" required placeholder="例如：Java 并发编程实践" /></label>
          <label class="wide">资源链接<input v-model.trim="createForm.url" required type="url" placeholder="https://..." /></label>
          <label>领域<select v-model="createForm.domain" required><option value="" disabled>选择领域</option><option v-for="option in domainOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
          <label>难度<select v-model="createForm.level"><option value="">不限</option><option v-for="option in levelOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
          <label>学习时长（分钟）<input v-model.number="createForm.durationMinutes" min="0" type="number" placeholder="可选" /></label>
          <label>标签<input v-model.trim="createForm.tags" placeholder="java,concurrency" /></label>
          <button class="primary-button submit-button" type="submit" :disabled="creating">{{ creating ? '提交中…' : '提交到待审队列' }}</button>
        </form>
      </section>
    </Transition>

    <div v-if="notice" class="notice" :class="notice.type" role="status"><span>{{ notice.text }}</span><button type="button" aria-label="关闭提示" @click="notice = null">×</button></div>

    <section class="operations-panel">
      <div class="panel-heading queue-heading">
        <div><span class="eyebrow">REVIEW QUEUE</span><h2>审核与质量队列</h2></div>
        <div class="result-count"><strong>{{ filteredResources.length }}</strong><span>/ {{ enrichedResources.length }} 条</span></div>
      </div>

      <div class="filter-bar">
        <label class="search-field"><span>检索</span><input v-model="filters.keyword" type="search" placeholder="标题、链接、标签或上传用户" /></label>
        <label><span>领域</span><select v-model="filters.domain"><option value="">全部领域</option><option v-for="option in domainOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
        <label><span>难度</span><select v-model="filters.level"><option value="">全部难度</option><option v-for="option in levelOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
        <label><span>状态</span><select v-model="filters.status"><option value="">全部状态</option><option value="PENDING">待审核</option><option value="ACTIVE">已上线</option><option value="INACTIVE">已下线</option></select></label>
        <label><span>风险</span><select v-model="filters.risk"><option value="">全部风险</option><option value="reported">仅看有举报</option></select></label>
      </div>

      <div class="queue-toolbar">
        <div class="filter-context">
          <template v-if="activeFilterCount"><span>{{ activeFilterCount }} 个筛选条件生效</span><button type="button" @click="clearFilters">清空筛选</button></template>
          <span v-else>默认按举报数、待审状态和最新资源排序</span>
        </div>
        <button class="export-button" type="button" :disabled="!filteredResources.length" @click="exportCsv">导出当前结果 CSV</button>
      </div>

      <div v-if="selectedIds.length" class="batch-bar">
        <div><strong>已选择 {{ selectedIds.length }} 条资源</strong><span>批量操作将应用于当前选择</span></div>
        <div>
          <button type="button" @click="requestBatch('ACTIVE')">批量上线</button>
          <button type="button" class="danger" @click="requestBatch('INACTIVE')">批量下线</button>
          <button type="button" class="quiet" @click="selectedIds = []">取消选择</button>
        </div>
      </div>

      <AdminResourceTable
        :resources="filteredResources"
        :selected-ids="selectedIds"
        :loading="pageLoading"
        @toggle="toggleSelection"
        @toggle-all="replaceSelection"
        @open="openDetail"
        @status="requestStatus"
        @reingest="requestReingest"
        @delete="requestDelete"
      />
    </section>

    <AdminResourceDrawer
      :resource="selectedResource"
      :feedbacks="feedbacks"
      :feedback-loading="feedbackLoading"
      :saving="saving"
      @close="selectedResource = null"
      @save="saveResource"
      @reingest="requestReingest"
    />

    <AdminResourceConfirm
      :open="Boolean(confirmAction)"
      :title="confirmCopy.title"
      :description="confirmCopy.description"
      :count="confirmCopy.count"
      :confirm-text="confirmCopy.button"
      :danger="confirmCopy.danger"
      :busy="actionBusy"
      @cancel="cancelConfirm"
      @confirm="executeConfirmedAction"
    />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { batchUpdateResourceStatus, createResource, deleteResource, getResourceFeedbacks, getResourceQualityStats, listResources, reingestResourceUrl, updateResource, updateResourceStatus } from '../api/resource';
import { getUserFacingError } from '../shared/api/errors';
import AdminResourceConfirm from '../features/admin/resources/AdminResourceConfirm.vue';
import AdminResourceDrawer from '../features/admin/resources/AdminResourceDrawer.vue';
import AdminResourceTable from '../features/admin/resources/AdminResourceTable.vue';
import { filterResources, mergeResourceQuality, resourcesToCsv, type ManagedResource, type ResourceFilters, type ResourceQuality, type ResourceStatus } from '../features/admin/resources/resourceManagement';

type ConfirmAction = { type: 'status'; resource: ManagedResource; status: ResourceStatus } | { type: 'batch'; status: ResourceStatus } | { type: 'reingest'; resource: ManagedResource } | { type: 'delete'; resource: ManagedResource };
type Notice = { type: 'success' | 'error'; text: string };
const route = useRoute();
const router = useRouter();
const resources = ref<Array<Omit<ManagedResource, 'avgRating' | 'feedbackCount' | 'invalidReportCount'>>>([]);
const qualityStats = ref<ResourceQuality[]>([]);
const pageLoading = ref(true);
const creating = ref(false);
const saving = ref(false);
const actionBusy = ref(false);
const createOpen = ref(false);
const selectedIds = ref<number[]>([]);
const selectedResource = ref<ManagedResource | null>(null);
const feedbacks = ref([]);
const feedbackLoading = ref(false);
const confirmAction = ref<ConfirmAction | null>(null);
const notice = ref<Notice | null>(null);
const filters = reactive<ResourceFilters>({ keyword: '', domain: '', level: '', status: '', risk: '' });
const createForm = reactive({ title: '', url: '', domain: '', level: '', durationMinutes: null as number | null, tags: '' });
const domainOptions = [
  { label: 'Java 后端', value: 'java' }, { label: 'Python', value: 'python' }, { label: '数据库 / SQL', value: 'database' },
  { label: '英语', value: 'english' }, { label: '数学', value: 'math' }, { label: '前端', value: 'frontend' },
  { label: 'Linux / 运维', value: 'devops' }, { label: '通用学习方法', value: 'general' }
];
const levelOptions = [{ label: '零基础', value: 'beginner' }, { label: '有一点基础', value: 'intermediate' }, { label: '进阶', value: 'advanced' }];

const enrichedResources = computed(() => mergeResourceQuality(resources.value, qualityStats.value));
const filteredResources = computed(() => filterResources(enrichedResources.value, filters));
const activeFilterCount = computed(() => Object.values(filters).filter(Boolean).length);
const summary = computed(() => {
  const all = enrichedResources.value;
  const rated = all.filter((item) => item.avgRating != null);
  return {
    pending: all.filter((item) => (item.status || 'PENDING') === 'PENDING').length,
    risky: all.filter((item) => item.invalidReportCount > 0).length,
    reports: all.reduce((sum, item) => sum + item.invalidReportCount, 0),
    rated: rated.length,
    average: rated.length ? (rated.reduce((sum, item) => sum + Number(item.avgRating), 0) / rated.length).toFixed(1) : '—'
  };
});
const confirmCopy = computed(() => {
  const action = confirmAction.value;
  if (!action) return { title: '', description: '', count: 0, button: '', danger: false };
  if (action.type === 'reingest') return { title: '重新摄取资源？', description: `将重新读取“${action.resource.title}”的来源链接并更新索引。`, count: 1, button: '确认重新摄取', danger: false };
  if (action.type === 'delete') return { title: '删除这条资源？', description: `“${action.resource.title}”将从资源列表和推荐范围中移除。`, count: 1, button: '确认删除', danger: true };
  const online = action.status === 'ACTIVE';
  const count = action.type === 'batch' ? selectedIds.value.length : 1;
  return { title: online ? '确认上线资源？' : '确认下线资源？', description: online ? '上线后资源可进入推荐与检索范围。' : '下线后资源将不再进入新的推荐结果。', count, button: online ? '确认上线' : '确认下线', danger: !online };
});

onMounted(async () => {
  syncFromRoute();
  await reloadAll();
});
watch(() => route.query, syncFromRoute);
watch(() => [filters.keyword, filters.domain, filters.level, filters.status, filters.risk], () => {
  const query = Object.fromEntries(Object.entries(filters).filter(([, value]) => Boolean(value)));
  if (JSON.stringify(query) !== JSON.stringify(route.query)) void router.replace({ query });
});

function syncFromRoute() {
  filters.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : '';
  filters.domain = typeof route.query.domain === 'string' ? route.query.domain : '';
  filters.level = typeof route.query.level === 'string' ? route.query.level : '';
  filters.status = typeof route.query.status === 'string' ? route.query.status : '';
  filters.risk = route.query.risk === 'reported' ? 'reported' : '';
}
async function reloadAll() {
  pageLoading.value = true;
  try {
    const [resourceData, qualityData] = await Promise.all([listResources(), getResourceQualityStats()]);
    resources.value = resourceData;
    qualityStats.value = qualityData;
    if (selectedResource.value) selectedResource.value = enrichedResources.value.find((item) => item.id === selectedResource.value?.id) ?? null;
  } catch { notice.value = { type: 'error', text: '资源队列加载失败，请检查服务连接后重试。' }; }
  finally { pageLoading.value = false; }
}
async function submitResource() {
  creating.value = true;
  try {
    await createResource({ ...createForm, level: createForm.level || null, durationMinutes: createForm.durationMinutes || null, tags: createForm.tags || null });
    Object.assign(createForm, { title: '', url: '', domain: '', level: '', durationMinutes: null, tags: '' });
    createOpen.value = false;
    notice.value = { type: 'success', text: '资源已提交，并进入待审核队列。' };
    await reloadAll();
  } catch { notice.value = { type: 'error', text: '资源提交失败，请检查链接与必填字段。' }; }
  finally { creating.value = false; }
}
function toggleSelection(id: number) { selectedIds.value = selectedIds.value.includes(id) ? selectedIds.value.filter((item) => item !== id) : [...selectedIds.value, id]; }
function replaceSelection(ids: number[]) { selectedIds.value = ids; }
function clearFilters() { Object.assign(filters, { keyword: '', domain: '', level: '', status: '', risk: '' }); }
function focusRisk() { filters.risk = 'reported'; document.querySelector('.operations-panel')?.scrollIntoView({ behavior: 'smooth' }); }
async function openDetail(resource: ManagedResource) {
  selectedResource.value = resource;
  feedbacks.value = [];
  feedbackLoading.value = true;
  try { feedbacks.value = await getResourceFeedbacks(resource.id); }
  catch { notice.value = { type: 'error', text: '反馈明细暂时无法加载，资源操作仍可继续。' }; }
  finally { feedbackLoading.value = false; }
}
async function saveResource(draft: Record<string, unknown>) {
  if (!selectedResource.value) return;
  saving.value = true;
  try {
    await updateResource(selectedResource.value.id, draft);
    notice.value = { type: 'success', text: '资源信息已保存。' };
    await reloadAll();
  } catch { notice.value = { type: 'error', text: '资源信息保存失败，请稍后重试。' }; }
  finally { saving.value = false; }
}
function requestStatus(resource: ManagedResource, status: ResourceStatus) { confirmAction.value = { type: 'status', resource, status }; }
function requestBatch(status: ResourceStatus) {
  if (!selectedIds.value.length) return;
  if (status === 'ACTIVE') {
    const blocked = enrichedResources.value.filter((resource) => selectedIds.value.includes(resource.id) && !canActivate(resource));
    if (blocked.length) {
      notice.value = { type: 'error', text: `有 ${blocked.length} 条资源尚未摄取成功，请先重新摄取或更换来源。` };
      return;
    }
  }
  confirmAction.value = { type: 'batch', status };
}
function requestReingest(resource: ManagedResource) { confirmAction.value = { type: 'reingest', resource }; }
function requestDelete(resource: ManagedResource) { confirmAction.value = { type: 'delete', resource }; }
function canActivate(resource: ManagedResource) {
  return !resource.ingestionStatus || ['NOT_STARTED', 'SUCCEEDED'].includes(resource.ingestionStatus);
}
function cancelConfirm() { if (!actionBusy.value) confirmAction.value = null; }
async function executeConfirmedAction() {
  const action = confirmAction.value;
  if (!action) return;
  actionBusy.value = true;
  try {
    if (action.type === 'batch') { await batchUpdateResourceStatus(selectedIds.value, action.status); selectedIds.value = []; }
    else if (action.type === 'status') await updateResourceStatus(action.resource.id, action.status);
    else if (action.type === 'delete') {
      await deleteResource(action.resource.id);
      selectedIds.value = selectedIds.value.filter((id) => id !== action.resource.id);
      if (selectedResource.value?.id === action.resource.id) selectedResource.value = null;
    } else await reingestResourceUrl(action.resource.id, action.resource.url, crypto.randomUUID());
    const successText = action.type === 'reingest' ? '重新摄取任务已提交。' : action.type === 'delete' ? '资源已删除。' : '资源状态已更新。';
    notice.value = { type: 'success', text: successText };
    confirmAction.value = null;
    await reloadAll();
  } catch (error) {
    notice.value = {
      type: 'error',
      text: getUserFacingError(error, action.type === 'reingest' ? '重新摄取任务提交失败。' : action.type === 'delete' ? '资源删除失败，请稍后重试。' : '资源状态更新失败，请稍后重试。')
    };
  }
  finally { actionBusy.value = false; }
}
function exportCsv() {
  const blob = new Blob([resourcesToCsv(filteredResources.value)], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = `learnflow-resources-${new Date().toISOString().slice(0, 10)}.csv`; anchor.click(); URL.revokeObjectURL(url);
}
</script>

<style scoped>
.resource-page{display:flex;flex-direction:column;gap:18px;padding:2px 0 36px;color:#203a36}.page-header{display:flex;justify-content:space-between;align-items:flex-end;gap:24px;padding:28px 30px;border:1px solid #dce8e3;border-radius:24px;background:radial-gradient(circle at 90% 10%,rgba(210,235,224,.9),transparent 30%),linear-gradient(135deg,#fbfdfc,#f0f7f3)}.eyebrow{font-size:10px;letter-spacing:.16em;color:#71827d}.page-header h1{margin:8px 0 7px;font:700 34px/1.1 Georgia,'Times New Roman',serif;color:#173d36}.page-header p{max-width:760px;margin:0;color:#637671;font-size:13px;line-height:1.75}.header-actions{display:flex;gap:8px;flex-shrink:0}.primary-button,.ghost-button,.export-button{border:0;border-radius:10px;padding:10px 15px;font-weight:700;cursor:pointer}.primary-button{background:#176b5a;color:#fff}.ghost-button,.export-button{background:#e5efeb;color:#375b53}.summary-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.summary-grid article{position:relative;overflow:hidden;display:grid;gap:4px;padding:17px 19px;border:1px solid #dfe8e4;border-radius:17px;background:#fff}.summary-grid article:after{content:'';position:absolute;right:-10px;bottom:-24px;width:70px;height:70px;border-radius:50%;background:#edf5f1}.summary-grid span{font-size:11px;color:#6c7e79}.summary-grid strong{font:700 27px/1.15 Georgia,serif;color:#183c35}.summary-grid small{color:#84918e}.summary-grid .pending{border-color:#eadfbf}.summary-grid .risky{border-color:#efd4ca}.summary-grid .risky strong{color:#a14834}.create-panel,.operations-panel{padding:22px;border:1px solid #dce7e3;border-radius:21px;background:#fff;box-shadow:0 10px 35px rgba(30,63,55,.04)}.panel-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}.panel-heading h2{margin:5px 0 0;font-size:18px}.panel-heading small{color:#71817d}.create-form{display:grid;grid-template-columns:1fr 1fr 150px 150px;gap:13px}.create-form label,.filter-bar label{display:grid;gap:6px;font-size:11px;color:#667873}.create-form .wide{grid-column:span 2}.create-form input,.create-form select,.filter-bar input,.filter-bar select{width:100%;box-sizing:border-box;border:1px solid #cddbd6;border-radius:9px;padding:10px;background:#fbfcfc;color:#213d38;font:inherit;font-size:13px}.submit-button{grid-column:1/-1}.notice{display:flex;align-items:center;justify-content:space-between;padding:12px 15px;border-radius:12px;font-size:13px}.notice.success{background:#e9f6ef;color:#22654e}.notice.error{background:#fff0ec;color:#9d402f}.notice button{border:0;background:transparent;color:inherit;font-size:18px;cursor:pointer}.queue-heading{margin-bottom:16px}.result-count{display:flex;align-items:baseline;gap:5px;color:#74837f}.result-count strong{font-size:22px;color:#193d36}.filter-bar{display:grid;grid-template-columns:minmax(220px,1.8fr) repeat(4,minmax(120px,1fr));gap:10px;padding:14px;border-radius:14px;background:#f2f6f4}.filter-bar label span{text-transform:uppercase;letter-spacing:.08em;font-size:9px}.queue-toolbar{display:flex;align-items:center;justify-content:space-between;padding:13px 1px}.filter-context{display:flex;align-items:center;gap:9px;color:#73827e;font-size:12px}.filter-context button{border:0;background:transparent;color:#176b5a;font-weight:700;cursor:pointer}.export-button{padding:8px 11px;font-size:11px}.export-button:disabled{opacity:.45}.batch-bar{display:flex;align-items:center;justify-content:space-between;gap:15px;margin-bottom:12px;padding:12px 14px;border-radius:13px;background:#173f38;color:#fff}.batch-bar>div:first-child{display:grid;gap:3px}.batch-bar span{font-size:10px;color:#b8d1c9}.batch-bar>div:last-child{display:flex;gap:7px}.batch-bar button{border:0;border-radius:8px;padding:8px 11px;background:#e8f4ef;color:#214f45;font-weight:700;cursor:pointer}.batch-bar button.danger{background:#f2d3c9;color:#873d2e}.batch-bar button.quiet{background:transparent;color:#c5d8d2}.reveal-enter-active,.reveal-leave-active{transition:.2s ease}.reveal-enter-from,.reveal-leave-to{opacity:0;transform:translateY(-8px)}@media(max-width:1050px){.create-form{grid-template-columns:1fr 1fr}.create-form .wide{grid-column:auto}.filter-bar{grid-template-columns:repeat(3,1fr)}.filter-bar .search-field{grid-column:span 2}}@media(max-width:760px){.page-header{align-items:flex-start;flex-direction:column;padding:22px}.page-header h1{font-size:29px}.summary-grid{grid-template-columns:1fr 1fr}.create-form,.filter-bar{grid-template-columns:1fr 1fr}.filter-bar .search-field{grid-column:1/-1}.operations-panel,.create-panel{padding:15px}.batch-bar,.queue-toolbar{align-items:flex-start;flex-direction:column}.batch-bar>div:last-child{width:100%;flex-wrap:wrap}}@media(max-width:480px){.summary-grid,.create-form,.filter-bar{grid-template-columns:1fr}.create-form .wide,.filter-bar .search-field{grid-column:auto}.header-actions{width:100%}.header-actions button{flex:1}.batch-bar button{flex:1}.panel-heading{align-items:flex-start}}
</style>
