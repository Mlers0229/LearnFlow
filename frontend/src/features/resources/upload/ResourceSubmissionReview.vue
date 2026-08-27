<script setup lang="ts">
import type { PreflightIssue, ResourceUploadForm, UploadProgressState } from './types'

defineProps<{
  form: ResourceUploadForm
  errors: PreflightIssue[]
  warnings: PreflightIssue[]
  progress: UploadProgressState
  submitting: boolean
}>()

function sourceSummary(form: ResourceUploadForm) {
  if (form.sourceType === 'URL') return form.url || '尚未填写链接'
  if (form.sourceType === 'TEXT') return form.text ? `${form.text.length.toLocaleString('zh-CN')} 字符正文` : '尚未粘贴正文'
  return form.file ? `${form.file.name} · ${(form.file.size / 1024 / 1024).toFixed(2)} MB` : '尚未选择文档'
}
</script>

<template>
  <section class="upload-step" aria-labelledby="review-heading">
    <div class="step-heading">
      <div>
        <span class="step-kicker">STEP 03 · 确认</span>
        <h2 id="review-heading">提交前最后检查</h2>
      </div>
      <span class="step-hint">系统会自动执行安全与重复校验</span>
    </div>

    <div class="review-grid">
      <article><span>来源</span><strong>{{ form.sourceType }}</strong><p>{{ sourceSummary(form) }}</p></article>
      <article><span>标题</span><strong>{{ form.title || '待填写' }}</strong><p>{{ form.domain || '未选择领域' }} · {{ form.level || '未标注难度' }}</p></article>
      <article><span>处理流程</span><strong>解析 → 索引 → 审核</strong><p>解析成功不等于审核通过，可在提交记录中持续跟踪。</p></article>
    </div>

    <div v-if="errors.length || warnings.length" class="issue-list" aria-live="polite">
      <div v-for="issue in errors" :key="issue.code" class="issue error"><b>需修正</b><span>{{ issue.message }}</span></div>
      <div v-for="issue in warnings" :key="issue.code" class="issue warning"><b>请确认</b><span>{{ issue.message }}</span></div>
    </div>
    <div v-else class="issue clear"><b>检查通过</b><span>未发现格式、安全或重复问题，可以提交。</span></div>

    <div v-if="progress.phase !== 'idle'" class="progress-card" :class="progress.phase" aria-live="polite">
      <div class="progress-copy"><div><span>处理进度</span><strong>{{ progress.title }}</strong></div><b>{{ progress.percent }}%</b></div>
      <div class="progress-track"><i :style="{ width: `${progress.percent}%` }" /></div>
      <p>{{ progress.detail }}</p>
    </div>
  </section>
</template>

<style scoped>
.upload-step{display:grid;gap:22px}.step-heading{display:flex;justify-content:space-between;gap:20px;align-items:flex-end}.step-heading h2{margin:6px 0 0;font-size:clamp(22px,3vw,32px);letter-spacing:-.04em;color:var(--lf-ink,#17342f)}.step-kicker{font-size:12px;font-weight:800;letter-spacing:.14em;color:#1b7868}.step-hint{color:#69807b;font-size:13px}.review-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.review-grid article{padding:17px;border-radius:16px;border:1px solid #dfe9e6;background:#fff;min-width:0}.review-grid span{font-size:11px;font-weight:800;letter-spacing:.1em;color:#6e837e}.review-grid strong{display:block;margin:7px 0;color:#254740;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.review-grid p{margin:0;color:#6a7f7a;font-size:13px;line-height:1.55;overflow-wrap:anywhere}.issue-list{display:grid;gap:8px}.issue{display:flex;gap:12px;align-items:flex-start;padding:13px 15px;border-radius:13px;font-size:14px}.issue b{flex:0 0 auto}.issue.error{background:#fff0ed;color:#8b3427;border:1px solid #f2cbc4}.issue.warning{background:#fff8e7;color:#795817;border:1px solid #ead9aa}.issue.clear{background:#edf8f4;color:#226757;border:1px solid #cce6dd}.progress-card{padding:18px;border-radius:17px;background:#f2f8f6;border:1px solid #d9e9e4}.progress-card.failed{background:#fff1ee;border-color:#f0c9c1}.progress-card.success{background:#edf9f3;border-color:#c7e8da}.progress-copy{display:flex;justify-content:space-between;gap:16px;align-items:center}.progress-copy>div{display:grid;gap:4px}.progress-copy span{font-size:11px;font-weight:800;letter-spacing:.1em;color:#6d817d}.progress-copy strong{color:#234840}.progress-copy>b{font-size:20px;color:#177865}.progress-track{height:7px;border-radius:999px;background:#d8e7e2;overflow:hidden;margin:14px 0 9px}.progress-track i{display:block;height:100%;border-radius:inherit;background:#1b8c75;transition:width .35s ease}.failed .progress-track i{background:#ba5748}.progress-card p{margin:0;color:#667d77;line-height:1.5;font-size:13px}@media(max-width:760px){.review-grid{grid-template-columns:1fr}.step-heading{align-items:flex-start;flex-direction:column}.step-hint{display:none}}
</style>
