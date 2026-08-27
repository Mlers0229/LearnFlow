<script setup lang="ts">
import type { ResourceUploadForm } from './types'

defineProps<{ form: ResourceUploadForm }>()
const emit = defineEmits<{
  title: [value: string]
  domain: [value: string]
  level: [value: string]
  estimatedMinutes: [value: number | null]
  tags: [value: string]
  rightsConfirmed: [value: boolean]
}>()

const domains = ['编程开发', '计算机基础', '数据与 AI', '产品设计', '语言学习', '职业成长', '其他']
const levels = ['入门', '进阶', '高级']

function toMinutes(value: string) {
  const parsed = Number(value)
  emit('estimatedMinutes', Number.isFinite(parsed) && parsed > 0 ? parsed : null)
}
</script>

<template>
  <section class="upload-step" aria-labelledby="metadata-heading">
    <div class="step-heading">
      <div>
        <span class="step-kicker">STEP 02 · 信息</span>
        <h2 id="metadata-heading">让资源更容易被发现</h2>
      </div>
      <span class="step-hint">带 * 的项目必须填写</span>
    </div>
    <div class="metadata-grid">
      <label class="field wide">
        <span>资源标题 *</span>
        <input :value="form.title" maxlength="300" placeholder="用一句话说明这份资源的主题" data-testid="resource-title" @input="emit('title', ($event.target as HTMLInputElement).value)">
        <small>{{ form.title.length }} / 300</small>
      </label>
      <label class="field">
        <span>知识领域 *</span>
        <select :value="form.domain" data-testid="resource-domain" @change="emit('domain', ($event.target as HTMLSelectElement).value)">
          <option value="" disabled>请选择领域</option>
          <option v-for="domain in domains" :key="domain" :value="domain">{{ domain }}</option>
        </select>
      </label>
      <label class="field">
        <span>难度</span>
        <select :value="form.level" @change="emit('level', ($event.target as HTMLSelectElement).value)">
          <option value="">暂不标注</option>
          <option v-for="level in levels" :key="level" :value="level">{{ level }}</option>
        </select>
      </label>
      <label class="field">
        <span>预计学习时长</span>
        <div class="input-suffix">
          <input :value="form.estimatedMinutes ?? ''" type="number" min="1" max="10000" placeholder="30" @input="toMinutes(($event.target as HTMLInputElement).value)">
          <em>分钟</em>
        </div>
      </label>
      <label class="field">
        <span>标签</span>
        <input :value="form.tags" maxlength="1000" placeholder="Vue, 前端, 实践" @input="emit('tags', ($event.target as HTMLInputElement).value)">
        <small>使用逗号分隔，便于后续检索</small>
      </label>
    </div>
    <label class="rights-check">
      <input :checked="form.rightsConfirmed" type="checkbox" data-testid="rights-confirmed" @change="emit('rightsConfirmed', ($event.target as HTMLInputElement).checked)">
      <span><strong>我确认有权提交并处理此内容</strong><small>请勿上传包含隐私、商业机密或未经许可的受版权保护内容。</small></span>
    </label>
  </section>
</template>

<style scoped>
.upload-step{display:grid;gap:24px}.step-heading{display:flex;justify-content:space-between;gap:20px;align-items:flex-end}.step-heading h2{margin:6px 0 0;font-size:clamp(22px,3vw,32px);letter-spacing:-.04em;color:var(--lf-ink,#17342f)}.step-kicker{font-size:12px;font-weight:800;letter-spacing:.14em;color:#1b7868}.step-hint{color:#69807b;font-size:13px}.metadata-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.field{display:grid;gap:8px}.field.wide{grid-column:1/-1}.field>span{font-weight:750;color:#274842}.field input,.field select{box-sizing:border-box;width:100%;border:1px solid #ceddd8;border-radius:13px;background:#fff;padding:13px 14px;color:#17342f;font:inherit;outline:none}.field input:focus,.field select:focus{border-color:#177865;box-shadow:0 0 0 3px rgba(23,120,101,.1)}.field small{color:#728782}.input-suffix{position:relative}.input-suffix input{padding-right:58px}.input-suffix em{position:absolute;right:14px;top:50%;transform:translateY(-50%);font-style:normal;color:#728782}.rights-check{display:flex;gap:12px;align-items:flex-start;padding:17px;border-radius:15px;background:#f5faf8;border:1px solid #e0ebe7;cursor:pointer}.rights-check input{margin-top:3px;width:17px;height:17px;accent-color:#177865}.rights-check span{display:grid;gap:4px;color:#294b45}.rights-check small{color:#6f837f;line-height:1.5}@media(max-width:700px){.metadata-grid{grid-template-columns:1fr}.field.wide{grid-column:auto}.step-heading{align-items:flex-start;flex-direction:column}.step-hint{display:none}}
</style>
