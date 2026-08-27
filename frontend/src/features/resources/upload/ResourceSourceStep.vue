<script setup lang="ts">
import { computed } from 'vue'
import { formatBytes } from './resourceUploadUtils'
import type { ResourceSourceType, ResourceUploadForm } from './types'

const props = defineProps<{ form: ResourceUploadForm }>()
const emit = defineEmits<{
  sourceType: [value: ResourceSourceType]
  url: [value: string]
  text: [value: string]
  file: [value: File | null]
}>()

const sourceTypes: Array<{ value: ResourceSourceType; eyebrow: string; title: string; detail: string }> = [
  { value: 'URL', eyebrow: '公开网页', title: '粘贴链接', detail: '适合文章、文档站和公开教程' },
  { value: 'TEXT', eyebrow: '纯文本', title: '粘贴正文', detail: '适合笔记、讲义和整理后的内容' },
  { value: 'DOCUMENT', eyebrow: '本地文件', title: '上传文档', detail: '支持 PDF、Word、TXT、MD、RTF' },
]

const textCount = computed(() => props.form.text.length.toLocaleString('zh-CN'))

function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  emit('file', input.files?.[0] || null)
}
</script>

<template>
  <section class="upload-step" aria-labelledby="source-heading">
    <div class="step-heading">
      <div>
        <span class="step-kicker">STEP 01 · 来源</span>
        <h2 id="source-heading">这份学习资料从哪里来？</h2>
      </div>
      <span class="step-hint">先选来源，再提供内容</span>
    </div>

    <div class="source-grid" role="radiogroup" aria-label="资源来源类型">
      <button
        v-for="item in sourceTypes"
        :key="item.value"
        class="source-card"
        :class="{ active: form.sourceType === item.value }"
        type="button"
        role="radio"
        :aria-checked="form.sourceType === item.value"
        @click="emit('sourceType', item.value)"
      >
        <span>{{ item.eyebrow }}</span>
        <strong>{{ item.title }}</strong>
        <small>{{ item.detail }}</small>
      </button>
    </div>

    <div class="source-editor">
      <label v-if="form.sourceType === 'URL'" class="field-block">
        <span>资源链接</span>
        <input
          :value="form.url"
          type="url"
          maxlength="2048"
          placeholder="https://example.com/learning-guide"
          data-testid="resource-url"
          @input="emit('url', ($event.target as HTMLInputElement).value)"
        >
        <small>仅抓取公开 HTTP/HTTPS 页面，内网地址、凭据和非标准端口会在提交前拦截。</small>
      </label>

      <label v-else-if="form.sourceType === 'TEXT'" class="field-block">
        <span>正文内容</span>
        <textarea
          :value="form.text"
          rows="10"
          maxlength="2000000"
          placeholder="粘贴需要整理和索引的学习内容……"
          data-testid="resource-text"
          @input="emit('text', ($event.target as HTMLTextAreaElement).value)"
        />
        <small>{{ textCount }} / 2,000,000 字符</small>
      </label>

      <label v-else class="file-drop" data-testid="resource-file-drop">
        <input type="file" accept=".pdf,.doc,.docx,.txt,.md,.rtf" @change="onFile">
        <span class="file-icon">↥</span>
        <strong>{{ form.file ? form.file.name : '选择需要处理的文档' }}</strong>
        <small v-if="form.file">{{ formatBytes(form.file.size) }} · 点击可重新选择</small>
        <small v-else>PDF、Word、TXT、Markdown、RTF，最大 10 MB</small>
      </label>
    </div>
  </section>
</template>

<style scoped>
.upload-step{display:grid;gap:24px}.step-heading{display:flex;justify-content:space-between;gap:20px;align-items:flex-end}.step-heading h2{margin:6px 0 0;font-size:clamp(22px,3vw,32px);letter-spacing:-.04em;color:var(--lf-ink,#17342f)}.step-kicker{font-size:12px;font-weight:800;letter-spacing:.14em;color:#1b7868}.step-hint{color:#69807b;font-size:13px}.source-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.source-card{display:grid;gap:6px;text-align:left;padding:18px;border:1px solid #dce8e4;border-radius:18px;background:#fff;cursor:pointer;color:#17342f;transition:.2s ease}.source-card:hover{transform:translateY(-2px);border-color:#8fc4b9}.source-card.active{border-color:#177865;background:#eef9f5;box-shadow:0 0 0 3px rgba(23,120,101,.08)}.source-card span{font-size:11px;font-weight:800;letter-spacing:.1em;color:#64817b}.source-card strong{font-size:17px}.source-card small{color:#69807b;line-height:1.5}.source-editor{padding:22px;border-radius:20px;background:#f6faf8;border:1px solid #e1ebe7}.field-block{display:grid;gap:9px}.field-block>span{font-weight:750;color:#274842}.field-block input,.field-block textarea{box-sizing:border-box;width:100%;border:1px solid #ceddd8;border-radius:13px;background:#fff;padding:13px 14px;color:#17342f;font:inherit;outline:none}.field-block textarea{resize:vertical;line-height:1.7}.field-block input:focus,.field-block textarea:focus{border-color:#177865;box-shadow:0 0 0 3px rgba(23,120,101,.1)}.field-block small,.file-drop small{color:#6f837f;line-height:1.5}.file-drop{min-height:190px;border:1.5px dashed #98bdb5;border-radius:16px;background:#fff;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;text-align:center;cursor:pointer;padding:20px;color:#274842}.file-drop input{position:absolute;opacity:0;pointer-events:none}.file-icon{display:grid;place-items:center;width:42px;height:42px;border-radius:50%;background:#e6f4ef;color:#177865;font-size:24px}@media(max-width:700px){.source-grid{grid-template-columns:1fr}.step-heading{align-items:flex-start;flex-direction:column}.step-hint{display:none}.source-editor{padding:16px}}
</style>
