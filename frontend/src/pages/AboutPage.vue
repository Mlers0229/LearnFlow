<template>
  <div class="about-page">
    <section class="about-hero" aria-labelledby="about-title">
      <div class="hero-copy">
        <div class="hero-eyebrow">LEARNFLOW / BETA {{ packageInfo.version }}</div>
        <h1 id="about-title">把学习目标，变成每天都能继续的行动。</h1>
        <p>
          LearnFlow 把计划生成、每日执行、练习反馈和学习复盘放在同一个工作台里。
          AI 提供建议与解释，真正的节奏、选择和结果始终由你掌握。
        </p>
        <div class="hero-actions">
          <RouterLink class="primary-action" to="/">开始规划 <ArrowUpRight :size="17" /></RouterLink>
          <RouterLink class="secondary-action" to="/history">查看学习记录</RouterLink>
        </div>
      </div>

      <div class="hero-orbit" aria-hidden="true">
        <div class="orbit-ring orbit-ring--outer" />
        <div class="orbit-ring orbit-ring--inner" />
        <div class="orbit-core">
          <Sparkles :size="25" />
          <strong>目标</strong>
          <span>持续向前</span>
        </div>
        <span class="orbit-node orbit-node--one">计划</span>
        <span class="orbit-node orbit-node--two">练习</span>
        <span class="orbit-node orbit-node--three">复盘</span>
      </div>
    </section>

    <section class="promise-strip" aria-label="产品原则">
      <div v-for="item in principles" :key="item.title">
        <component :is="item.icon" :size="18" aria-hidden="true" />
        <span><strong>{{ item.title }}</strong><small>{{ item.detail }}</small></span>
      </div>
    </section>

    <section class="section-block workflow-section" aria-labelledby="workflow-title">
      <header class="section-heading">
        <div><span>HOW IT WORKS</span><h2 id="workflow-title">一条清楚的学习路径</h2></div>
        <p>减少在工具之间来回切换，把注意力留给今天真正要完成的事情。</p>
      </header>

      <ol class="workflow-list">
        <li v-for="(step, index) in workflow" :key="step.title">
          <span class="step-index">0{{ index + 1 }}</span>
          <div><strong>{{ step.title }}</strong><p>{{ step.detail }}</p></div>
          <component :is="step.icon" :size="20" aria-hidden="true" />
        </li>
      </ol>
    </section>

    <section class="section-block" aria-labelledby="capability-title">
      <header class="section-heading">
        <div><span>CAPABILITIES</span><h2 id="capability-title">围绕学习，而不是堆叠功能</h2></div>
        <p>下列能力均已连接到当前工作台；结果质量仍取决于输入、资源与所配置的模型。</p>
      </header>

      <div class="capability-grid">
        <article v-for="item in capabilities" :key="item.title" class="capability-card">
          <div class="capability-top">
            <span class="capability-icon"><component :is="item.icon" :size="21" aria-hidden="true" /></span>
            <span class="status-dot"><i /> 已接入</span>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.detail }}</p>
          <RouterLink :to="item.to">{{ item.action }} <ArrowRight :size="15" /></RouterLink>
        </article>
      </div>
    </section>

    <section class="trust-grid">
      <article class="data-panel" aria-labelledby="data-title">
        <header>
          <span class="panel-icon"><ShieldCheck :size="22" aria-hidden="true" /></span>
          <div><span>YOUR DATA</span><h2 id="data-title">你的数据，由你掌控</h2></div>
        </header>
        <div class="data-list">
          <div v-for="item in dataPractices" :key="item.title">
            <Check :size="17" aria-hidden="true" />
            <span><strong>{{ item.title }}</strong><small>{{ item.detail }}</small></span>
          </div>
        </div>
        <RouterLink class="text-link" to="/profile">前往安全与隐私设置 <ArrowRight :size="15" /></RouterLink>
      </article>

      <article class="release-panel" aria-labelledby="release-title">
        <span class="release-label">CURRENT RELEASE</span>
        <div class="release-version"><strong>Beta</strong><span>Web {{ packageInfo.version }}</span></div>
        <h2 id="release-title">仍在持续打磨的学习工作台</h2>
        <p>当前版本聚焦计划执行、练习复盘、资源共建与 AI 辅学。功能可能继续演进，重要学习结果请结合自己的判断复核。</p>
        <dl>
          <div><dt>前端</dt><dd>Vue 3 · TypeScript · Vite</dd></div>
          <div><dt>服务</dt><dd>Spring Boot · FastAPI</dd></div>
          <div><dt>问题反馈</dt><dd>请联系当前实例管理员</dd></div>
        </dl>
      </article>
    </section>

    <section class="closing-callout">
      <div><span>NEXT STEP</span><strong>从一个真实目标开始。</strong></div>
      <p>先生成一份可执行计划，再根据每天的反馈调整它。</p>
      <RouterLink to="/">创建学习计划 <ArrowUpRight :size="17" /></RouterLink>
    </section>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import {
  ArrowRight,
  ArrowUpRight,
  BookOpenCheck,
  BrainCircuit,
  Check,
  ClipboardCheck,
  FileStack,
  Flag,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Target,
} from 'lucide-vue-next'
import packageInfo from '../../package.json'

const principles = [
  { title: '行动优先', detail: '计划落到每天', icon: Target },
  { title: '反馈闭环', detail: '练习推动调整', icon: RefreshCw },
  { title: '透明辅助', detail: 'AI 结果可复核', icon: BrainCircuit },
  { title: '数据可控', detail: '支持导出与删除', icon: ShieldCheck },
]

const workflow = [
  { title: '定义目标', detail: '说明要学什么、可投入多久，以及期望达到的程度。', icon: Flag },
  { title: '生成路径', detail: '系统把目标拆成阶段、学习日和可完成的任务。', icon: FileStack },
  { title: '执行与练习', detail: '按日推进，结合资源、任务细化和练习题巩固。', icon: ClipboardCheck },
  { title: '回看与调整', detail: '从历史记录和练习反馈中识别下一步重点。', icon: RefreshCw },
]

const capabilities = [
  { title: '结构化学习计划', detail: '根据目标、周期和投入时间生成阶段清楚的每日行动。', action: '开始生成', to: '/', icon: Target },
  { title: '执行与历史复盘', detail: '集中查看学习日、任务状态、资源和已有练习记录。', action: '查看历史', to: '/history', icon: FileStack },
  { title: '练习反馈沉淀', detail: '保存作答、评估与待复习标记，让错误成为下一次输入。', action: '回顾练习', to: '/exercise-review', icon: BookOpenCheck },
  { title: '上下文 AI 辅学', detail: '围绕当前学习日提问、解释难点，并保留可见引用来源。', action: '进入对话', to: '/chat', icon: BrainCircuit },
]

const dataPractices = [
  { title: '用途清楚', detail: '账号、计划、练习与资源数据用于提供对应学习功能。' },
  { title: '操作可见', detail: '模型生成内容以辅助结果呈现，需要你结合上下文复核。' },
  { title: '生命周期可控', detail: '可在个人设置中申请数据副本，或发起账号永久删除。' },
]
</script>

<style scoped>
.about-page{display:grid;gap:24px;color:#193b35}.about-hero{position:relative;overflow:hidden;display:grid;grid-template-columns:minmax(0,1.18fr) minmax(320px,.82fr);align-items:center;min-height:480px;padding:clamp(34px,6vw,72px);border-radius:32px;background:#173f37;color:#f9f5eb}.about-hero:before{content:"";position:absolute;inset:0;background:radial-gradient(circle at 12% 4%,rgba(255,255,255,.1),transparent 32%),linear-gradient(120deg,transparent 55%,rgba(223,174,88,.08))}.hero-copy,.hero-orbit{position:relative;z-index:1}.hero-eyebrow,.section-heading span,.data-panel header>div>span,.release-label,.closing-callout span{font-size:10px;font-weight:850;letter-spacing:.18em}.hero-eyebrow{color:#a9d9ca}.hero-copy h1{max-width:760px;margin:16px 0 20px;font-family:Georgia,"Times New Roman",serif;font-size:clamp(42px,6vw,76px);font-weight:500;line-height:1.03;letter-spacing:-.055em}.hero-copy p{max-width:650px;margin:0;color:#c8d9d4;font-size:15px;line-height:1.85}.hero-actions{display:flex;flex-wrap:wrap;gap:11px;margin-top:30px}.hero-actions a,.closing-callout>a{display:inline-flex;align-items:center;justify-content:center;gap:8px;padding:12px 17px;border-radius:12px;font-size:13px;font-weight:850;text-decoration:none}.primary-action{background:#e4b760;color:#183e36}.secondary-action{border:1px solid rgba(255,255,255,.2);color:#f8f4eb}.hero-orbit{width:min(100%,390px);aspect-ratio:1;justify-self:end}.orbit-ring{position:absolute;border:1px solid rgba(255,255,255,.15);border-radius:50%}.orbit-ring--outer{inset:0}.orbit-ring--inner{inset:22%}.orbit-core{position:absolute;inset:35%;display:grid;place-items:center;align-content:center;gap:5px;border-radius:50%;background:#f7f1e4;color:#173f37;box-shadow:0 20px 60px rgba(0,0,0,.25)}.orbit-core svg{color:#b78028}.orbit-core strong{font-family:Georgia,serif;font-size:24px}.orbit-core span{font-size:10px;color:#668079}.orbit-node{position:absolute;padding:9px 13px;border:1px solid rgba(255,255,255,.16);border-radius:999px;background:rgba(255,255,255,.09);backdrop-filter:blur(8px);font-size:11px;font-weight:800}.orbit-node--one{top:10%;left:19%}.orbit-node--two{right:2%;top:48%}.orbit-node--three{bottom:8%;left:24%}.promise-strip{display:grid;grid-template-columns:repeat(4,1fr);overflow:hidden;border:1px solid #dce7e3;border-radius:20px;background:#fff}.promise-strip>div{display:flex;align-items:center;gap:12px;padding:18px 20px;border-right:1px solid #e4ebe8}.promise-strip>div:last-child{border-right:0}.promise-strip svg{color:#1c7b67}.promise-strip span{display:grid;gap:2px}.promise-strip strong{font-size:12px}.promise-strip small{color:#778b85;font-size:10px}.section-block{padding:clamp(26px,4vw,46px);border-radius:28px;background:#fff;box-shadow:0 14px 38px rgba(43,68,60,.055)}.workflow-section{background:#f1eee6}.section-heading{display:flex;align-items:end;justify-content:space-between;gap:30px;margin-bottom:30px}.section-heading span,.data-panel header>div>span{color:#1d7a67}.section-heading h2,.data-panel h2,.release-panel h2{margin:8px 0 0;font-family:Georgia,"Times New Roman",serif;font-size:clamp(28px,4vw,42px);font-weight:500;line-height:1.12;letter-spacing:-.035em}.section-heading>p{max-width:480px;margin:0;color:#71837e;font-size:13px;line-height:1.7}.workflow-list{display:grid;grid-template-columns:repeat(4,1fr);margin:0;padding:0;list-style:none}.workflow-list li{position:relative;display:grid;grid-template-columns:1fr auto;gap:15px;min-height:190px;padding:20px;border-top:1px solid #cfd9d4;border-right:1px solid #d7ded9}.workflow-list li:last-child{border-right:0}.step-index{grid-column:1/-1;color:#a87a32;font-family:Georgia,serif;font-size:14px}.workflow-list strong{font-size:16px}.workflow-list p{margin:8px 0 0;color:#71817c;font-size:12px;line-height:1.65}.workflow-list svg{color:#1c7865}.capability-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:13px}.capability-card{display:grid;min-height:230px;padding:22px;border:1px solid #dfe8e5;border-radius:20px;background:#fbfcfa}.capability-top{display:flex;align-items:center;justify-content:space-between}.capability-icon{display:grid;width:42px;height:42px;place-items:center;border-radius:13px;background:#e8f4ef;color:#1a7663}.status-dot{display:flex;align-items:center;gap:6px;color:#6d837d;font-size:10px;font-weight:700}.status-dot i{width:6px;height:6px;border-radius:50%;background:#1b956f}.capability-card h3{margin:24px 0 8px;font-size:20px}.capability-card p{margin:0;color:#6d817b;font-size:13px;line-height:1.7}.capability-card>a{display:inline-flex;align-items:center;gap:6px;align-self:end;justify-self:start;margin-top:23px;color:#1a715f;font-size:12px;font-weight:850;text-decoration:none}.trust-grid{display:grid;grid-template-columns:minmax(0,1.15fr) minmax(320px,.85fr);gap:16px}.data-panel,.release-panel{padding:clamp(26px,4vw,40px);border-radius:26px}.data-panel{border:1px solid #dbe8e4;background:#f8fbfa}.data-panel header{display:flex;align-items:flex-start;gap:14px}.panel-icon{display:grid;width:44px;height:44px;flex:0 0 44px;place-items:center;border-radius:14px;background:#dff0ea;color:#176d5b}.data-panel h2{font-size:32px}.data-list{display:grid;gap:16px;margin:28px 0}.data-list>div{display:flex;align-items:flex-start;gap:11px}.data-list svg{flex:0 0 auto;margin-top:2px;color:#188064}.data-list span{display:grid;gap:4px}.data-list strong{font-size:13px}.data-list small{color:#70847e;font-size:12px;line-height:1.55}.text-link{display:inline-flex;align-items:center;gap:7px;color:#176e5c;font-size:12px;font-weight:850;text-decoration:none}.release-panel{background:#213c37;color:#f7f4eb}.release-label{color:#a8d7c9}.release-version{display:flex;align-items:end;justify-content:space-between;margin:24px 0 30px;padding-bottom:20px;border-bottom:1px solid rgba(255,255,255,.13)}.release-version strong{font-family:Georgia,serif;font-size:52px;font-weight:500;color:#e7bc69}.release-version span{font-size:11px;color:#bfd0cb}.release-panel h2{font-size:31px}.release-panel>p{color:#bfd0cb;font-size:12px;line-height:1.75}.release-panel dl{display:grid;gap:10px;margin:24px 0 0}.release-panel dl>div{display:flex;justify-content:space-between;gap:20px;padding-top:10px;border-top:1px solid rgba(255,255,255,.1)}.release-panel dt{color:#8fa7a0;font-size:10px}.release-panel dd{margin:0;font-size:11px;text-align:right}.closing-callout{display:grid;grid-template-columns:minmax(240px,.8fr) minmax(0,1fr) auto;align-items:center;gap:30px;padding:25px 30px;border:1px solid #e4d8c2;border-radius:22px;background:#faf4e8}.closing-callout div{display:grid;gap:5px}.closing-callout span{color:#a16f25}.closing-callout strong{font-family:Georgia,serif;font-size:24px}.closing-callout p{margin:0;color:#6f7f77;font-size:12px}.closing-callout>a{background:#173f37;color:#fff}
@media(max-width:1050px){.about-hero,.trust-grid{grid-template-columns:1fr}.hero-orbit{display:none}.workflow-list{grid-template-columns:repeat(2,1fr)}.workflow-list li:nth-child(2){border-right:0}.promise-strip{grid-template-columns:repeat(2,1fr)}.promise-strip>div:nth-child(2){border-right:0}.promise-strip>div:nth-child(-n+2){border-bottom:1px solid #e4ebe8}.closing-callout{grid-template-columns:1fr auto}.closing-callout>p{grid-column:1/-1;grid-row:2}}
@media(max-width:680px){.about-page{gap:16px}.about-hero{min-height:0;padding:30px 22px;border-radius:24px}.hero-copy h1{font-size:43px}.hero-actions a{width:100%}.promise-strip{grid-template-columns:1fr}.promise-strip>div{border-right:0;border-bottom:1px solid #e4ebe8}.promise-strip>div:last-child{border-bottom:0}.section-block{padding:25px 19px;border-radius:23px}.section-heading{display:grid;gap:13px;margin-bottom:23px}.workflow-list,.capability-grid{grid-template-columns:1fr}.workflow-list li{min-height:150px;border-right:0}.trust-grid{grid-template-columns:1fr}.data-panel,.release-panel{padding:25px 20px;border-radius:22px}.release-version strong{font-size:44px}.closing-callout{grid-template-columns:1fr;padding:23px}.closing-callout>p{grid-row:auto}.closing-callout>a{width:100%;box-sizing:border-box}}
@media(prefers-reduced-motion:no-preference){.orbit-ring--outer{animation:orbit-spin 36s linear infinite}.orbit-ring--inner{animation:orbit-spin 24s linear infinite reverse}}@keyframes orbit-spin{to{transform:rotate(360deg)}}
</style>
