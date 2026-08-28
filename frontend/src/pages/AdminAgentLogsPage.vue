<template>
  <main class="log-page">
    <header class="page-header">
      <div><span class="eyebrow">ADMIN · AI OPERATIONS</span><h1>Agent 调用日志</h1><p>按调用链观察 Agent、模型、耗时和异常摘要。页面仅展示服务端已经脱敏、限长的调试信息。</p></div>
      <div class="header-actions"><button type="button" class="quiet" @click="focusExceptions">查看异常</button><button type="button" class="primary" :disabled="loading" @click="load">{{ loading ? '刷新中…' : '刷新日志' }}</button></div>
    </header>

    <section class="summary-grid" aria-label="Agent 调用概览">
      <article><span>调用记录</span><strong>{{ summary.total }}</strong><small>当前保留窗口</small></article>
      <article><span>调用链</span><strong>{{ summary.traces }}</strong><small>不同 Trace ID</small></article>
      <article class="warning"><span>慢调用</span><strong>{{ summary.slow }}</strong><small>P95 {{ summary.p95 }} ms</small></article>
      <article class="danger"><span>异常摘要</span><strong>{{ summary.failed }}</strong><small>无响应或包含失败信号</small></article>
    </section>

    <div v-if="error" class="notice error" role="alert"><span>{{ error }}</span><button type="button" @click="load">重试</button></div>

    <section class="operations-panel">
      <div class="panel-heading"><div><span class="eyebrow">TRACE EXPLORER</span><h2>调用链检索</h2></div><div class="result-count"><strong>{{ filtered.length }}</strong><span>/ {{ logs.length }} 条</span></div></div>
      <div class="filter-grid">
        <label class="trace-field"><span>Trace ID</span><input v-model.trim="filters.traceId" type="search" placeholder="输入完整或部分 Trace ID" /></label>
        <label><span>任务 ID</span><input v-model.trim="filters.taskId" type="search" placeholder="输入关联任务标识" /></label>
        <label><span>Agent</span><select v-model="filters.agent"><option value="">全部 Agent</option><option v-for="item in agents" :key="item" :value="item">{{ item }}</option></select></label>
        <label><span>模型</span><select v-model="filters.model"><option value="">全部模型</option><option v-for="item in models" :key="item" :value="item">{{ item }}</option></select></label>
        <label><span>状态</span><select v-model="filters.status"><option value="">全部状态</option><option value="SUCCESS">正常</option><option value="SLOW">慢调用</option><option value="FAILED">异常</option></select></label>
        <label><span>最低耗时</span><select v-model="filters.duration"><option value="">不限</option><option value="1000">≥ 1 秒</option><option value="3000">≥ 3 秒</option><option value="10000">≥ 10 秒</option></select></label>
      </div>
      <div class="filter-context"><span v-if="activeFilterCount">{{ activeFilterCount }} 个筛选条件生效</span><span v-else>默认按最近调用排序，最多读取 200 条</span><button v-if="activeFilterCount" type="button" @click="clearFilters">清空筛选</button></div>

      <div v-if="loading" class="table-state" role="status"><strong>正在读取调用日志</strong><span>日志读取不会影响正在运行的学习任务。</span></div>
      <div v-else-if="!filtered.length" class="table-state"><strong>没有匹配的调用记录</strong><span>可以清空筛选，或先执行一次计划、资源、细化或练习任务。</span></div>
      <div v-else class="table-scroll">
        <table>
          <thead><tr><th>时间 / Trace</th><th>Agent / 模型</th><th>耗时</th><th>状态</th><th>请求摘要</th><th aria-label="操作" /></tr></thead>
          <tbody><tr v-for="log in paged" :key="log.id"><td><strong>{{ formatDate(log.createdAt) }}</strong><button class="trace-link" type="button" @click="open(log)">{{ shortTrace(log.traceId) }}</button></td><td><strong>{{ log.agentName || 'Unknown Agent' }}</strong><span>{{ log.modelName || '未使用模型' }}</span></td><td><strong>{{ log.durationMs ?? 0 }} ms</strong><span>{{ durationHint(log.durationMs) }}</span></td><td><span class="status" :class="logStatus(log).toLowerCase()">{{ statusLabel[logStatus(log)] }}</span></td><td class="payload-cell">{{ payloadPreview(log.requestPayload) }}</td><td><button class="detail-button" type="button" @click="open(log)">查看详情</button></td></tr></tbody>
        </table>
      </div>
      <footer v-if="filtered.length > pageSize" class="pagination"><span>第 {{ page }} / {{ pageCount }} 页</span><div><button type="button" :disabled="page === 1" @click="page--">上一页</button><button type="button" :disabled="page === pageCount" @click="page++">下一页</button></div></footer>
    </section>

    <AdminAgentLogDrawer :log="selected" @close="selected = null" />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listAgentLogs } from '../api/adminLogs'
import AdminAgentLogDrawer from '../features/admin/logs/AdminAgentLogDrawer.vue'
import { filterAgentLogs, logStatus, payloadPreview, statusLabel, summarizeAgentLogs, type AgentLogFilters, type AgentLogRecord } from '../features/admin/logs/agentLog'
import { getUserFacingError } from '../shared/api/errors'

const route = useRoute(); const router = useRouter()
const logs = ref<AgentLogRecord[]>([]); const loading = ref(true); const error = ref(''); const selected = ref<AgentLogRecord | null>(null); const page = ref(1); const pageSize = 20
const filters = reactive<AgentLogFilters>({ traceId: '', taskId: '', agent: '', model: '', status: '', duration: '' })
const filtered = computed(() => filterAgentLogs(logs.value, filters)); const summary = computed(() => summarizeAgentLogs(logs.value))
const agents = computed(() => [...new Set(logs.value.map((item) => item.agentName).filter((value): value is string => Boolean(value)))].sort())
const models = computed(() => [...new Set(logs.value.map((item) => item.modelName).filter((value): value is string => Boolean(value && value !== '-')))].sort())
const activeFilterCount = computed(() => Object.values(filters).filter(Boolean).length); const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize))); const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

onMounted(() => { syncFromRoute(); void load() })
watch(() => route.query, syncFromRoute)
watch(() => [filters.traceId, filters.taskId, filters.agent, filters.model, filters.status, filters.duration], () => { page.value = 1; const query = Object.fromEntries(Object.entries(filters).filter(([, value]) => Boolean(value))); if (JSON.stringify(query) !== JSON.stringify(route.query)) void router.replace({ query }) })
watch(pageCount, (count) => { if (page.value > count) page.value = count })
function syncFromRoute() { filters.traceId = typeof route.query.traceId === 'string' ? route.query.traceId : ''; filters.taskId = typeof route.query.taskId === 'string' ? route.query.taskId : ''; filters.agent = typeof route.query.agent === 'string' ? route.query.agent : ''; filters.model = typeof route.query.model === 'string' ? route.query.model : ''; filters.status = ['SUCCESS','SLOW','FAILED','UNKNOWN'].includes(String(route.query.status || '')) ? route.query.status as AgentLogFilters['status'] : ''; filters.duration = ['1000','3000','10000'].includes(String(route.query.duration || '')) ? route.query.duration as AgentLogFilters['duration'] : '' }
async function load() { loading.value = true; error.value = ''; try { logs.value = await listAgentLogs(200) } catch (failure) { error.value = getUserFacingError(failure, 'Agent 调用日志加载失败，请检查服务后重试。') } finally { loading.value = false } }
function clearFilters() { Object.assign(filters, { traceId: '', taskId: '', agent: '', model: '', status: '', duration: '' }) }
function focusExceptions() { filters.status = 'FAILED'; document.querySelector('.operations-panel')?.scrollIntoView({ behavior: 'smooth' }) }
function open(log: AgentLogRecord) { selected.value = log }
function shortTrace(value?: string | null) { const trace = String(value || '无 Trace ID'); return trace.length > 22 ? `${trace.slice(0, 10)}…${trace.slice(-8)}` : trace }
function formatDate(value?: string | null) { return value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }) : '时间未知' }
function durationHint(value?: number | null) { const ms = Number(value || 0); return ms >= 10000 ? '超过 10 秒' : ms >= 3000 ? '需要关注' : ms >= 1000 ? '一般' : '快速' }
</script>

<style scoped>
.log-page{display:flex;flex-direction:column;gap:17px;padding:2px 0 36px;color:#203b36}.page-header{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;padding:28px 30px;border:1px solid #dce7e3;border-radius:24px;background:radial-gradient(circle at 88% 10%,rgba(221,232,202,.95),transparent 30%),linear-gradient(135deg,#fbfdfc,#eff6f3)}.eyebrow{color:#71817d;font-size:9px;letter-spacing:.16em}.page-header h1{margin:7px 0;font:700 33px/1.1 Georgia,serif;color:#173d36}.page-header p{max-width:760px;margin:0;color:#637570;font-size:13px;line-height:1.75}.header-actions{display:flex;gap:8px;flex-shrink:0}.header-actions button{border:0;border-radius:10px;padding:10px 14px;font-weight:750;cursor:pointer}.header-actions .primary{background:#176b5a;color:#fff}.header-actions .quiet{background:#e5efeb;color:#365d53}.summary-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:11px}.summary-grid article{display:grid;gap:4px;padding:17px 19px;border:1px solid #dfe8e4;border-radius:17px;background:#fff}.summary-grid span{color:#6e7e7a;font-size:10px}.summary-grid strong{font:700 27px Georgia,serif;color:#183c35}.summary-grid small{color:#84918e;font-size:9px}.summary-grid .warning{border-color:#eadfbf}.summary-grid .danger{border-color:#efd4ca}.summary-grid .danger strong{color:#a14834}.notice{display:flex;justify-content:space-between;align-items:center;padding:12px 14px;border-radius:12px;font-size:12px}.notice.error{background:#fff0ec;color:#98402e}.notice button{border:0;background:transparent;color:inherit;font-weight:750;cursor:pointer}.operations-panel{padding:22px;border:1px solid #dce7e3;border-radius:21px;background:#fff;box-shadow:0 10px 35px rgba(30,63,55,.04)}.panel-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}.panel-heading h2{margin:5px 0 0;font-size:18px}.result-count{display:flex;align-items:baseline;gap:5px;color:#74837f}.result-count strong{font-size:22px;color:#193d36}.filter-grid{display:grid;grid-template-columns:minmax(220px,1.8fr) repeat(5,minmax(120px,1fr));gap:10px;padding:14px;border-radius:14px;background:#f2f6f4}.filter-grid label{display:grid;gap:6px;color:#667873;font-size:10px}.filter-grid input,.filter-grid select{width:100%;box-sizing:border-box;border:1px solid #cddbd6;border-radius:9px;padding:10px;background:#fbfcfc;color:#213d38;font:inherit;font-size:12px}.filter-context{display:flex;justify-content:space-between;min-height:34px;align-items:center;color:#74837f;font-size:10px}.filter-context button{border:0;background:transparent;color:#176b5a;font-weight:750;cursor:pointer}.table-state{display:grid;min-height:260px;place-content:center;gap:7px;text-align:center;border:1px dashed #ccd9d5;border-radius:15px;background:#fafcfb;color:#71807c}.table-state strong{color:#27433d}.table-scroll{overflow-x:auto;border:1px solid #dfe8e5;border-radius:15px}table{width:100%;min-width:1050px;border-collapse:collapse}th{padding:11px 13px;text-align:left;color:#6d7d79;background:#f3f7f5;border-bottom:1px solid #dfe8e5;font-size:9px;letter-spacing:.08em;text-transform:uppercase}td{padding:13px;border-bottom:1px solid #e8efec;color:#314a45;font-size:11px;vertical-align:middle}tbody tr:last-child td{border-bottom:0}tbody tr:hover{background:#f7faf9}td>strong,td>span{display:block}.trace-link{max-width:180px;margin-top:4px;padding:0;border:0;background:transparent;color:#55706a;font:10px ui-monospace,monospace;cursor:pointer}.payload-cell{max-width:320px;color:#667a75;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.status{display:inline-flex;padding:4px 8px;border-radius:99px;font-size:10px;font-weight:750}.status.success{background:#e4f5ed;color:#176449}.status.slow{background:#fff3d9;color:#8d6513}.status.failed{background:#fff0eb;color:#9d402f}.status.unknown{background:#edf0ef;color:#63716e}.detail-button{border:0;border-radius:8px;padding:7px 9px;background:#e6efec;color:#315c52;font-weight:750;cursor:pointer}.pagination{display:flex;align-items:center;justify-content:space-between;margin-top:12px;color:#71807d;font-size:10px}.pagination div{display:flex;gap:6px}.pagination button{border:0;border-radius:8px;padding:7px 10px;background:#e8f0ed;color:#365e54;cursor:pointer}.pagination button:disabled{opacity:.45}.header-actions button:disabled{opacity:.55}@media(max-width:1100px){.filter-grid{grid-template-columns:repeat(3,1fr)}.trace-field{grid-column:span 2}}@media(max-width:720px){.page-header{align-items:flex-start;flex-direction:column;padding:22px}.page-header h1{font-size:29px}.header-actions{width:100%}.header-actions button{flex:1}.summary-grid{grid-template-columns:1fr 1fr}.operations-panel{padding:16px}.filter-grid{grid-template-columns:1fr 1fr}.trace-field{grid-column:1/-1}}@media(max-width:420px){.summary-grid{grid-template-columns:1fr}.filter-grid{grid-template-columns:1fr}.filter-grid label{grid-column:auto}}
</style>
