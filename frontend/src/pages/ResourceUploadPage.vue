<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import MyResourceSubmissions from '../features/resources/upload/MyResourceSubmissions.vue'
import ResourceMetadataStep from '../features/resources/upload/ResourceMetadataStep.vue'
import ResourceSourceStep from '../features/resources/upload/ResourceSourceStep.vue'
import ResourceSubmissionReview from '../features/resources/upload/ResourceSubmissionReview.vue'
import { useResourceUpload } from '../features/resources/upload/useResourceUpload'
import type { ResourceRecord, UploadStep } from '../features/resources/upload/types'

const message = useMessage()
const {
  form, step, records, recordsLoading, submitting, progress, errors, warnings, canSubmit,
  statusCounts, loadRecords, setSourceType, submit, retry, reset,
} = useResourceUpload()

const steps = [
  { value: 1 as UploadStep, label: '选择来源', detail: '链接、正文或文档' },
  { value: 2 as UploadStep, label: '补充信息', detail: '标题、领域和标签' },
  { value: 3 as UploadStep, label: '检查提交', detail: '安全校验与处理进度' },
]

const sourceReady = computed(() => form.sourceType === 'URL' ? Boolean(form.url.trim()) : form.sourceType === 'TEXT' ? Boolean(form.text.trim()) : Boolean(form.file))
const metadataReady = computed(() => Boolean(form.title.trim() && form.domain && form.rightsConfirmed))

function goTo(target: UploadStep) {
  if (target > 1 && !sourceReady.value) {
    message.warning('请先提供资源内容')
    return
  }
  if (target > 2 && !metadataReady.value) {
    message.warning('请补全必填信息并确认内容权利')
    return
  }
  step.value = target
}

async function handleSubmit() {
  try {
    await submit()
    message.success('资源已提交，可在“我的提交”继续跟踪')
    setTimeout(() => document.querySelector('#my-submissions')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 150)
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : '提交失败，请稍后重试。')
  }
}

async function handleRetry(record: ResourceRecord) {
  try {
    await retry(record)
    if (record.sourceType === 'URL') message.success('已创建新的处理任务')
    else message.info('已回填资源信息，请重新提供内容')
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : '重试失败')
  }
}

onMounted(async () => {
  try { await loadRecords() } catch { message.warning('提交记录暂时无法同步，可稍后刷新') }
})
</script>

<template>
  <main class="resource-page">
    <section class="upload-hero">
      <div class="hero-copy">
        <span class="hero-kicker">RESOURCE INTAKE · 资源工作台</span>
        <h1>把一份好资料，<br><em>变成可学习的知识。</em></h1>
        <p>分三步提交公开链接、正文或文档。系统会先做安全和重复检查，再解析、索引并进入审核。</p>
        <a href="#upload-workbench" class="hero-action">开始提交 <span>↓</span></a>
      </div>
      <div class="hero-stats" aria-label="我的资源统计">
        <article><strong>{{ statusCounts.total }}</strong><span>全部提交</span></article>
        <article><strong>{{ statusCounts.pending }}</strong><span>等待审核</span></article>
        <article><strong>{{ statusCounts.active }}</strong><span>已经上架</span></article>
        <article><strong>{{ statusCounts.inactive }}</strong><span>需要完善</span></article>
      </div>
    </section>

    <section id="upload-workbench" class="workbench">
      <nav class="step-nav" aria-label="资源提交步骤">
        <button v-for="item in steps" :key="item.value" type="button" :class="{ active: step === item.value, done: step > item.value }" @click="goTo(item.value)">
          <b>{{ step > item.value ? '✓' : item.value }}</b>
          <span><strong>{{ item.label }}</strong><small>{{ item.detail }}</small></span>
        </button>
      </nav>

      <div class="step-surface">
        <ResourceSourceStep
          v-if="step === 1"
          :form="form"
          @source-type="setSourceType"
          @url="form.url = $event"
          @text="form.text = $event"
          @file="form.file = $event"
        />
        <ResourceMetadataStep
          v-else-if="step === 2"
          :form="form"
          @title="form.title = $event"
          @domain="form.domain = $event"
          @level="form.level = $event"
          @estimated-minutes="form.estimatedMinutes = $event"
          @tags="form.tags = $event"
          @rights-confirmed="form.rightsConfirmed = $event"
        />
        <ResourceSubmissionReview v-else :form="form" :errors="errors" :warnings="warnings" :progress="progress" :submitting="submitting" />

        <div class="step-actions">
          <button v-if="step > 1" type="button" class="secondary" :disabled="submitting" @click="step = (step - 1) as UploadStep">返回上一步</button>
          <span v-else class="privacy-note">内容仅用于学习资源处理与审核</span>
          <button v-if="step < 3" type="button" class="primary" @click="goTo((step + 1) as UploadStep)">继续</button>
          <button v-else type="button" class="primary" data-testid="submit-resource" :disabled="!canSubmit" @click="handleSubmit">{{ submitting ? '处理中…' : '确认并提交' }}</button>
        </div>
        <div v-if="progress.phase === 'success'" class="success-actions">
          <button type="button" @click="reset">继续提交另一份资源</button>
          <a href="#my-submissions">查看我的提交</a>
        </div>
      </div>

      <aside class="workbench-aside">
        <span>提交前须知</span>
        <h2>安全、清晰、可追踪</h2>
        <ol>
          <li><b>01</b><div><strong>来源安全</strong><p>不抓取内网地址、含凭据链接和异常端口。</p></div></li>
          <li><b>02</b><div><strong>格式限制</strong><p>文档最大 10 MB，正文最多 200 万字符。</p></div></li>
          <li><b>03</b><div><strong>双重状态</strong><p>解析完成后仍需审核，通过后才会正式上架。</p></div></li>
        </ol>
      </aside>
    </section>

    <section class="records-surface">
      <MyResourceSubmissions :records="records" :loading="recordsLoading" :busy="submitting" @refresh="loadRecords" @retry="handleRetry" />
    </section>
  </main>
</template>

<style scoped>
.resource-page{min-height:100%;padding:clamp(18px,3vw,34px);background:#f4f0e8;color:#193b35}.upload-hero{position:relative;overflow:hidden;display:grid;grid-template-columns:minmax(0,1.2fr) minmax(300px,.8fr);gap:clamp(28px,5vw,70px);align-items:end;padding:clamp(28px,5vw,66px);border-radius:30px;background:#173f37;color:#f8f4e9}.upload-hero:after{content:"";position:absolute;width:430px;height:430px;border-radius:50%;right:-150px;top:-220px;border:1px solid rgba(255,255,255,.12);box-shadow:0 0 0 70px rgba(255,255,255,.035),0 0 0 140px rgba(255,255,255,.025)}.hero-copy,.hero-stats{position:relative;z-index:1}.hero-kicker{font-size:11px;font-weight:800;letter-spacing:.18em;color:#a9d8ca}.hero-copy h1{margin:15px 0 18px;font-family:Georgia,"Times New Roman",serif;font-size:clamp(38px,6vw,72px);font-weight:500;line-height:1.02;letter-spacing:-.055em}.hero-copy h1 em{font-weight:400;color:#e4b65f}.hero-copy p{max-width:690px;margin:0;color:#c8d8d3;font-size:15px;line-height:1.8}.hero-action{display:inline-flex;gap:12px;align-items:center;margin-top:26px;color:#fff;text-decoration:none;font-weight:800}.hero-action span{display:grid;place-items:center;width:30px;height:30px;border-radius:50%;background:#e3b55f;color:#173f37}.hero-stats{display:grid;grid-template-columns:repeat(2,1fr);border:1px solid rgba(255,255,255,.14);border-radius:22px;background:rgba(255,255,255,.055);backdrop-filter:blur(8px)}.hero-stats article{display:grid;gap:5px;padding:22px;border-right:1px solid rgba(255,255,255,.11);border-bottom:1px solid rgba(255,255,255,.11)}.hero-stats article:nth-child(2n){border-right:0}.hero-stats article:nth-child(n+3){border-bottom:0}.hero-stats strong{font-family:Georgia,serif;font-size:32px;color:#f3c979}.hero-stats span{font-size:12px;color:#c2d4cf}.workbench{display:grid;grid-template-columns:minmax(0,1fr) 260px;gap:22px;margin-top:24px}.step-nav{grid-column:1/-1;display:grid;grid-template-columns:repeat(3,1fr);padding:9px;border-radius:18px;background:#e7e3da}.step-nav button{display:flex;align-items:center;gap:11px;border:0;border-radius:13px;background:transparent;padding:12px;text-align:left;color:#637872;cursor:pointer}.step-nav button.active{background:#fff;color:#173f37;box-shadow:0 5px 18px rgba(35,62,55,.08)}.step-nav button.done{color:#27715f}.step-nav b{display:grid;place-items:center;flex:0 0 30px;width:30px;height:30px;border:1px solid #becdc8;border-radius:50%}.step-nav .active b,.step-nav .done b{border-color:#197b67;background:#197b67;color:#fff}.step-nav span{display:grid;gap:2px}.step-nav strong{font-size:13px}.step-nav small{font-size:11px;color:#81918d}.step-surface,.records-surface{padding:clamp(20px,4vw,36px);border-radius:24px;background:#fff;box-shadow:0 12px 34px rgba(51,65,59,.065)}.step-actions{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-top:28px;padding-top:20px;border-top:1px solid #e4ece9}.step-actions button,.success-actions button,.success-actions a{border-radius:12px;padding:12px 18px;font:inherit;font-weight:800;cursor:pointer}.step-actions .primary{border:0;background:#197b67;color:#fff;box-shadow:0 8px 18px rgba(25,123,103,.2)}.step-actions .secondary{border:1px solid #cadbd6;background:#fff;color:#365d54}.step-actions button:disabled{opacity:.5;cursor:not-allowed}.privacy-note{color:#758984;font-size:12px}.success-actions{display:flex;gap:11px;justify-content:flex-end;margin-top:12px}.success-actions button,.success-actions a{border:0;background:#edf6f3;color:#236b5a;text-decoration:none;font-size:13px}.workbench-aside{align-self:start;position:sticky;top:18px;padding:24px;border-radius:23px;background:#fcf7ea;border:1px solid #eadfc8}.workbench-aside>span{font-size:11px;font-weight:800;letter-spacing:.15em;color:#997129}.workbench-aside h2{margin:8px 0 22px;font-family:Georgia,serif;font-size:25px;font-weight:500;color:#3e4d42}.workbench-aside ol{list-style:none;display:grid;gap:18px;margin:0;padding:0}.workbench-aside li{display:flex;gap:11px}.workbench-aside li>b{color:#b28532;font-size:11px}.workbench-aside li div{display:grid;gap:4px}.workbench-aside li strong{font-size:14px;color:#3c514b}.workbench-aside li p{margin:0;color:#75817d;font-size:12px;line-height:1.55}.records-surface{margin-top:24px}@media(max-width:920px){.upload-hero{grid-template-columns:1fr}.workbench{grid-template-columns:1fr}.workbench-aside{position:static;display:none}}@media(max-width:620px){.resource-page{padding:12px}.upload-hero{padding:28px 22px;border-radius:22px}.hero-copy h1{font-size:40px}.hero-stats article{padding:16px}.step-nav{gap:4px}.step-nav button{justify-content:center;padding:9px 5px}.step-nav button span{display:none}.step-surface,.records-surface{padding:18px;border-radius:20px}.privacy-note{max-width:130px}.step-actions button{padding:11px 14px}}
</style>
