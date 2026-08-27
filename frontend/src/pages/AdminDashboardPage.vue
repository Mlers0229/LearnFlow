<script setup lang="ts">
import { defineAsyncComponent, onMounted, ref } from 'vue'
import { AlertTriangle, ArrowRight, BarChart3, Bot, CheckCircle2, Clock3, RefreshCw, Settings2 } from 'lucide-vue-next'
import AdminMetricCard from '../features/admin/dashboard/AdminMetricCard.vue'
import { useAdminDashboard } from '../features/admin/dashboard/useAdminDashboard'

const AdminTrendChart = defineAsyncComponent(() => import('../features/admin/dashboard/AdminTrendChart.vue'))
const { loading, error, updatedAt, metrics, risks, trends, latestPlans, taskCounts, modelConfigured, defaultModel, load } = useAdminDashboard()
const showCharts = ref(false)
const timeText = () => updatedAt.value ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(updatedAt.value) : '尚未同步'
const taskLabels: Record<string, string> = { PENDING: '等待', RUNNING: '运行中', PAUSED: '暂停', SUCCEEDED: '成功', FAILED: '失败', CANCELLED: '取消' }
const dateText = (value?: string) => value ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '时间未知'
onMounted(load)
</script>

<template>
  <div class="admin-overview">
    <section class="overview-hero" aria-labelledby="overview-title">
      <div class="hero-copy">
        <span class="hero-kicker">OPERATIONS OVERVIEW</span>
        <h1 id="overview-title">先看风险，再决定今天处理什么。</h1>
        <p>这里汇总用户、计划、持久任务、Agent 调用和资源审核的真实数据。所有样本范围与派生口径都会明确展示。</p>
        <div class="hero-meta"><span><i />{{ loading ? '正在同步' : '接口已连接' }}</span><span>最近更新 {{ timeText() }}</span></div>
      </div>
      <div class="hero-actions">
        <div><span>DEFAULT MODEL</span><strong>{{ defaultModel }}</strong><small>{{ modelConfigured ? '配置可用' : '需要管理员补全配置' }}</small></div>
        <button type="button" :disabled="loading" data-testid="dashboard-refresh" @click="load"><RefreshCw :size="17" :class="{ spinning: loading }" />{{ loading ? '同步中…' : '刷新数据' }}</button>
      </div>
    </section>

    <div v-if="error" class="error-banner" role="alert"><AlertTriangle :size="19" /><span><strong>总览暂时无法同步</strong><small>{{ error }}</small></span><button type="button" @click="load">重试</button></div>

    <section class="metrics-grid" aria-label="核心运营指标">
      <AdminMetricCard v-for="metric in metrics" :key="metric.key" :metric="metric" :loading="loading && !updatedAt" />
    </section>

    <section class="main-grid">
      <article class="surface risk-surface" aria-labelledby="risk-title">
        <header class="surface-heading"><div><span>ATTENTION</span><h2 id="risk-title">需要处理</h2><p>仅展示由当前数据触发的事项。</p></div><strong>{{ risks.length }}</strong></header>
        <div v-if="loading && !updatedAt" class="list-loading" aria-label="正在加载风险事项"><i v-for="n in 3" :key="n" /></div>
        <div v-else-if="risks.length" class="risk-list">
          <RouterLink v-for="risk in risks" :key="risk.key" :to="{ path: risk.to, query: risk.query }" :class="['risk-item', `is-${risk.severity}`]">
            <span class="risk-mark"><AlertTriangle v-if="risk.severity !== 'notice'" :size="17" /><Settings2 v-else :size="17" /></span>
            <span><strong>{{ risk.title }}</strong><small>{{ risk.detail }}</small><em>{{ risk.action }} <ArrowRight :size="13" /></em></span><b>{{ risk.value }}</b>
          </RouterLink>
        </div>
        <div v-else class="healthy-empty"><CheckCircle2 :size="27" /><strong>当前没有待处理异常</strong><p>资源审核、日志摘要与模型配置均未触发风险规则。</p></div>
      </article>

      <article class="surface queue-surface" aria-labelledby="queue-title">
        <header class="surface-heading"><div><span>DURABLE QUEUE</span><h2 id="queue-title">异步任务状态</h2><p>来自持久任务队列，不是页面估算值。</p></div><Clock3 :size="23" /></header>
        <dl class="queue-list">
          <div v-for="status in Object.keys(taskLabels)" :key="status" :class="{ danger: status === 'FAILED' && taskCounts[status] }"><dt><i />{{ taskLabels[status] }}</dt><dd>{{ Number(taskCounts[status] || 0).toLocaleString('zh-CN') }}</dd></div>
        </dl>
        <p class="surface-footnote">失败任务已有后端管理员重放接口；专用处理页面将在任务运维批次补齐。</p>
      </article>
    </section>

    <section class="surface trend-surface" aria-labelledby="trend-title">
      <header class="surface-heading trend-heading"><div><span>SAMPLED TRENDS</span><h2 id="trend-title">变化趋势</h2><p>图表按需加载；下方文字摘要始终可读。</p></div><button type="button" data-testid="toggle-dashboard-charts" @click="showCharts = !showCharts"><BarChart3 :size="16" />{{ showCharts ? '收起图表' : '查看趋势图' }}</button></header>
      <div class="trend-summaries"><article v-for="item in trends" :key="item.key"><strong>{{ item.title }}</strong><p>{{ item.summary }}</p><span>{{ item.points.length }} 个数据点</span></article></div>
      <AdminTrendChart v-if="showCharts" class="trend-charts" :series="trends" />
    </section>

    <section class="secondary-grid">
      <article class="surface recent-surface" aria-labelledby="recent-title">
        <header class="surface-heading"><div><span>RECENT PLANS</span><h2 id="recent-title">最近计划样本</h2><p>聚合接口返回的最近 50 份非取消计划。</p></div></header>
        <div v-if="latestPlans.length" class="recent-list"><div v-for="plan in latestPlans" :key="plan.id"><span><strong>{{ plan.title || `计划 #${plan.id}` }}</strong><small>{{ dateText(plan.createdAt || plan.startDate) }}</small></span><em>{{ plan.status || 'active' }}</em></div></div>
        <div v-else class="compact-empty">当前没有计划样本。</div>
      </article>
      <article class="surface model-surface" aria-labelledby="model-title">
        <Bot :size="29" /><span>MODEL CONTROL</span><h2 id="model-title">{{ modelConfigured ? '模型策略已连接' : '模型策略需要配置' }}</h2><p>{{ modelConfigured ? `当前默认模型为 ${defaultModel}。可继续检查目录同步、凭据和生效范围。` : '补齐提供商、API Base、凭据和默认模型后，用户侧才能稳定使用统一策略。' }}</p><RouterLink to="/admin/models">进入模型配置 <ArrowRight :size="15" /></RouterLink>
      </article>
    </section>
  </div>
</template>

<style scoped>
.admin-overview{display:grid;gap:18px;color:#183630}.overview-hero{position:relative;overflow:hidden;display:grid;grid-template-columns:minmax(0,1.35fr) minmax(280px,.65fr);gap:32px;align-items:end;padding:clamp(30px,5vw,56px);border-radius:28px;background:#183d36;color:#f8f5ec}.overview-hero:after{content:"";position:absolute;width:380px;height:380px;right:-130px;top:-230px;border:1px solid rgba(255,255,255,.12);border-radius:50%;box-shadow:0 0 0 65px rgba(255,255,255,.025),0 0 0 130px rgba(255,255,255,.018)}.hero-copy,.hero-actions{position:relative;z-index:1}.hero-kicker,.surface-heading span,.model-surface>span{font-size:9px;font-weight:850;letter-spacing:.17em;color:#a4d3c6}.hero-copy h1{max-width:780px;margin:14px 0 17px;font-family:Georgia,serif;font-size:clamp(38px,5vw,62px);font-weight:500;line-height:1.02;letter-spacing:-.05em}.hero-copy p{max-width:720px;margin:0;color:#c4d7d1;font-size:13px;line-height:1.75}.hero-meta{display:flex;flex-wrap:wrap;gap:16px;margin-top:24px;color:#a9beb8;font-size:10px}.hero-meta span{display:flex;align-items:center;gap:7px}.hero-meta i{width:7px;height:7px;border-radius:50%;background:#54c79d;box-shadow:0 0 0 4px rgba(84,199,157,.13)}.hero-actions{display:grid;gap:15px;padding:20px;border:1px solid rgba(255,255,255,.14);border-radius:19px;background:rgba(255,255,255,.055)}.hero-actions>div{display:grid;gap:5px}.hero-actions span{color:#a9beb8;font-size:9px;font-weight:800;letter-spacing:.14em}.hero-actions strong{overflow:hidden;font-size:19px;text-overflow:ellipsis;white-space:nowrap}.hero-actions small{color:#a9beb8;font-size:10px}.hero-actions button,.trend-heading button,.error-banner button{display:inline-flex;align-items:center;justify-content:center;gap:7px;border:0;border-radius:11px;padding:11px 14px;font:inherit;font-size:11px;font-weight:850;cursor:pointer}.hero-actions button{background:#e2b664;color:#183d36}.hero-actions button:disabled{opacity:.65}.spinning{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}.error-banner{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:12px;padding:14px 16px;border:1px solid #ebc7c0;border-radius:15px;background:#fff6f3;color:#93473b}.error-banner span{display:grid;gap:2px}.error-banner strong{font-size:12px}.error-banner small{font-size:10px}.error-banner button{border:1px solid #d9aaa1;background:#fff;color:#93473b}.metrics-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:10px}.main-grid,.secondary-grid{display:grid;grid-template-columns:minmax(0,1.35fr) minmax(300px,.65fr);gap:14px}.surface{min-width:0;padding:22px;border:1px solid #dce6e2;border-radius:21px;background:#fff;box-shadow:0 12px 30px rgba(37,63,55,.045)}.surface-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.surface-heading span{color:#277864}.surface-heading h2{margin:5px 0 0;font-family:Georgia,serif;font-size:25px;font-weight:500}.surface-heading p{margin:5px 0 0;color:#768781;font-size:10px;line-height:1.5}.surface-heading>strong{font-family:Georgia,serif;font-size:34px;font-weight:500;color:#c18430}.risk-list{display:grid;gap:9px;margin-top:20px}.risk-item{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:12px;align-items:start;padding:14px;border:1px solid #e0e7e4;border-radius:14px;color:#213f39;text-decoration:none;transition:.16s}.risk-item:hover,.risk-item:focus-visible{border-color:#8fb9ad;background:#fbfdfc;outline:none}.risk-mark{display:grid;width:34px;height:34px;place-items:center;border-radius:10px;background:#f2eee3;color:#9b6b24}.risk-item.is-danger .risk-mark{background:#f8e5e1;color:#a84538}.risk-item>span:nth-child(2){display:grid;gap:4px}.risk-item strong{font-size:12px}.risk-item small{color:#72837e;font-size:10px;line-height:1.5}.risk-item em{display:flex;align-items:center;gap:4px;margin-top:3px;color:#267460;font-size:9px;font-style:normal;font-weight:800}.risk-item>b{color:#9d563c;font-family:Georgia,serif;font-size:18px;font-weight:500}.healthy-empty,.compact-empty{display:grid;place-items:center;margin-top:20px;padding:28px;border-radius:14px;background:#f5faf7;text-align:center}.healthy-empty svg{color:#2d9577}.healthy-empty strong{margin-top:9px;font-size:12px}.healthy-empty p,.compact-empty{color:#778a84;font-size:10px}.list-loading{display:grid;gap:9px;margin-top:20px}.list-loading i{height:66px;border-radius:14px;background:#f0f4f2}.queue-list{display:grid;gap:0;margin:19px 0 0}.queue-list>div{display:flex;justify-content:space-between;padding:11px 2px;border-bottom:1px solid #edf1ef}.queue-list dt{display:flex;align-items:center;gap:8px;color:#647972;font-size:11px}.queue-list dt i{width:6px;height:6px;border-radius:50%;background:#8ba89f}.queue-list dd{margin:0;font-family:Georgia,serif;font-size:17px}.queue-list .danger dt,.queue-list .danger dd{color:#ad493b}.queue-list .danger dt i{background:#bd5b4e}.surface-footnote{margin:15px 0 0;color:#87938f;font-size:9px;line-height:1.55}.trend-heading{align-items:center}.trend-heading button{border:1px solid #cbdcd6;background:#fff;color:#236d5b}.trend-summaries{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:20px}.trend-summaries article{padding:14px;border-radius:14px;background:#f4f7f5}.trend-summaries strong{font-size:11px}.trend-summaries p{margin:6px 0;color:#697d76;font-size:10px;line-height:1.5}.trend-summaries span{color:#8b9793;font-size:8px}.trend-charts{margin-top:12px}.chart-loading{display:grid;height:180px;place-items:center;color:#758680;font-size:11px}.recent-list{display:grid;margin-top:17px}.recent-list>div{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:12px 0;border-bottom:1px solid #edf1ef}.recent-list>div>span{display:grid;min-width:0;gap:3px}.recent-list strong{overflow:hidden;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.recent-list small{color:#81908b;font-size:9px}.recent-list em{padding:5px 8px;border-radius:999px;background:#eef5f2;color:#367463;font-size:8px;font-style:normal}.model-surface{background:#f6f0e4}.model-surface>svg{color:#a97529}.model-surface>span{display:block;margin-top:20px;color:#9b6d2b}.model-surface h2{margin:7px 0 8px;font-family:Georgia,serif;font-size:27px;font-weight:500}.model-surface p{margin:0;color:#6e766e;font-size:11px;line-height:1.65}.model-surface a{display:inline-flex;align-items:center;gap:6px;margin-top:20px;color:#226d5b;font-size:10px;font-weight:850;text-decoration:none}
@media(max-width:1280px){.metrics-grid{grid-template-columns:repeat(3,1fr)}}@media(max-width:900px){.overview-hero,.main-grid,.secondary-grid{grid-template-columns:1fr}.metrics-grid{grid-template-columns:repeat(2,1fr)}.trend-summaries{grid-template-columns:1fr}}@media(max-width:560px){.admin-overview{gap:13px}.overview-hero{padding:28px 21px;border-radius:22px}.hero-copy h1{font-size:39px}.metrics-grid{grid-template-columns:1fr}.surface{padding:18px;border-radius:18px}.trend-heading{align-items:flex-start;flex-direction:column}.trend-heading button{width:100%}.error-banner{grid-template-columns:auto 1fr}.error-banner button{grid-column:1/-1}.risk-item{grid-template-columns:auto minmax(0,1fr)}.risk-item>b{grid-column:2}.secondary-grid{grid-template-columns:1fr}}
</style>
