<template>
  <Teleport to="body">
    <div v-if="resource" class="drawer-layer">
      <button class="backdrop" type="button" aria-label="关闭资源详情" @click="$emit('close')" />
      <aside class="drawer" aria-labelledby="resource-drawer-title">
        <header>
          <div>
            <div class="eyebrow">资源 #{{ resource.id }}</div>
            <h2 id="resource-drawer-title">{{ resource.title }}</h2>
          </div>
          <button class="close" type="button" aria-label="关闭资源详情" @click="$emit('close')">×</button>
        </header>

        <div class="drawer-body">
          <section class="risk-panel" :class="{ clear: resource.invalidReportCount === 0 }">
            <div><span>质量风险</span><strong>{{ resource.invalidReportCount ? `${resource.invalidReportCount} 次举报` : '暂未发现举报' }}</strong></div>
            <div><span>用户反馈</span><strong>{{ resource.feedbackCount }} 条 · {{ resource.avgRating == null ? '暂无评分' : `${Number(resource.avgRating).toFixed(1)} 分` }}</strong></div>
          </section>

          <section class="section">
            <div class="section-head"><h3>资源信息</h3><span>{{ statusText(resource.status) }}</span></div>
            <div class="form-grid">
              <label class="full">标题<input v-model="draft.title" /></label>
              <label class="full">资源链接<input v-model="draft.url" type="url" /></label>
              <label>领域<select v-model="draft.domain"><option v-for="option in domains" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
              <label>难度<select v-model="draft.level"><option value="">不限</option><option value="beginner">零基础</option><option value="intermediate">有一点基础</option><option value="advanced">进阶</option></select></label>
              <label>时长（分钟）<input v-model.number="draft.durationMinutes" type="number" min="0" /></label>
              <label>标签<input v-model="draft.tags" placeholder="以逗号分隔" /></label>
            </div>
            <button class="save-button" type="button" :disabled="saving" @click="$emit('save', { ...draft })">{{ saving ? '保存中…' : '保存资源信息' }}</button>
          </section>

          <section class="section metadata">
            <div class="section-head"><h3>摄取信息</h3></div>
            <dl>
              <div><dt>来源类型</dt><dd>{{ resource.sourceType || '—' }}</dd></div>
              <div><dt>摄取状态</dt><dd>{{ resource.ingestionStatus || 'NOT_STARTED' }}</dd></div>
              <div><dt>当前任务</dt><dd>{{ resource.currentIngestionId ? `#${resource.currentIngestionId}` : '—' }}</dd></div>
              <div><dt>上传用户</dt><dd>{{ resource.uploaderUsername || '—' }}</dd></div>
              <div><dt>更新时间</dt><dd>{{ formatDate(resource.updatedAt || resource.createdAt) }}</dd></div>
            </dl>
            <button v-if="canViewResource(resource)" type="button" class="secondary-button" @click="openResource(resource)">{{ resourceActionLabel(resource) }}</button>
            <button v-if="resource.url" type="button" class="secondary-button" @click="$emit('reingest', resource)">重新摄取该链接</button>
          </section>

          <section class="section feedback-section">
            <div class="section-head"><h3>最近反馈</h3><span>{{ feedbacks.length }} 条</span></div>
            <div v-if="feedbackLoading" class="feedback-empty">正在加载反馈…</div>
            <div v-else-if="!feedbacks.length" class="feedback-empty">这条资源还没有用户反馈。</div>
            <article v-for="feedback in feedbacks" v-else :key="feedback.id" class="feedback-card" :class="{ reported: feedback.reportedInvalid }">
              <div><strong>{{ feedback.rating ? `${feedback.rating} 分` : '未评分' }}</strong><span v-if="feedback.reportedInvalid">已举报无效</span></div>
              <p>{{ feedback.comment || '用户未填写文字反馈。' }}</p>
              <time>{{ formatDate(feedback.createdAt) }}</time>
            </article>
          </section>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue';
import { statusText, type ManagedResource } from './resourceManagement';
import { useResourceViewer } from '../../resources/viewer/useResourceViewer';

interface ResourceFeedback { id: number; rating?: number | null; comment?: string | null; reportedInvalid?: boolean; createdAt?: string | null }
const props = defineProps<{ resource: ManagedResource | null; feedbacks: ResourceFeedback[]; feedbackLoading: boolean; saving: boolean }>();
defineEmits<{ close: []; save: [draft: Record<string, unknown>]; reingest: [resource: ManagedResource] }>();
const draft = reactive({ title: '', url: '', domain: '', level: '', durationMinutes: null as number | null, tags: '' });
const { canViewResource, resourceActionLabel, openResource } = useResourceViewer();
const domains = [
  { label: 'Java 后端', value: 'java' }, { label: 'Python', value: 'python' }, { label: '数据库 / SQL', value: 'database' },
  { label: '英语', value: 'english' }, { label: '数学', value: 'math' }, { label: '前端', value: 'frontend' },
  { label: 'Linux / 运维', value: 'devops' }, { label: '通用学习方法', value: 'general' }
];
watch(() => props.resource, (resource) => {
  if (!resource) return;
  Object.assign(draft, { title: resource.title ?? '', url: resource.url ?? '', domain: resource.domain ?? '', level: resource.level ?? '', durationMinutes: resource.durationMinutes ?? null, tags: resource.tags ?? '' });
}, { immediate: true });
function formatDate(value?: string | null) { return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'; }
</script>

<style scoped>
.drawer-layer{position:fixed;inset:0;z-index:2200}.backdrop{position:absolute;inset:0;width:100%;border:0;background:rgba(13,31,29,.38);backdrop-filter:blur(3px)}.drawer{position:absolute;top:0;right:0;width:min(560px,100%);height:100%;background:#f8faf9;box-shadow:-18px 0 55px rgba(17,38,34,.2);overflow:hidden}.drawer header{height:110px;display:flex;align-items:flex-start;justify-content:space-between;padding:25px 28px;background:#173e37;color:#fff}.eyebrow{font-size:10px;letter-spacing:.15em;text-transform:uppercase;color:#a9c7be}.drawer h2{margin:7px 0 0;max-width:430px;font-size:21px;line-height:1.35}.close{border:0;background:transparent;color:#d7e8e2;font-size:29px;cursor:pointer}.drawer-body{height:calc(100% - 110px);overflow-y:auto;padding:20px 24px 36px}.risk-panel{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:15px;padding:16px;border-radius:16px;background:#fff1eb;border:1px solid #f2d1c6}.risk-panel.clear{background:#edf7f2;border-color:#d2e9df}.risk-panel div{display:grid;gap:5px}.risk-panel span{font-size:11px;color:#788580}.risk-panel strong{color:#293f3b}.section{margin-top:14px;padding:19px;border:1px solid #dde7e3;border-radius:17px;background:#fff}.section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:15px}.section-head h3{margin:0;color:#203c37;font-size:15px}.section-head span{font-size:11px;color:#788783}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:13px}.form-grid label{display:grid;gap:6px;font-size:11px;color:#667974}.form-grid .full{grid-column:1/-1}.form-grid input,.form-grid select{width:100%;box-sizing:border-box;border:1px solid #cddad6;border-radius:9px;padding:9px 10px;background:#fbfcfc;color:#1d3934;font:inherit;font-size:13px}.save-button,.secondary-button{width:100%;margin-top:15px;border:0;border-radius:10px;padding:10px 14px;background:#176b5a;color:#fff;font-weight:700;cursor:pointer}.save-button:disabled{opacity:.55}.secondary-button{background:#e8f1ee;color:#315b52}.metadata dl{display:grid;grid-template-columns:1fr 1fr;gap:13px;margin:0}.metadata dl div{display:grid;gap:4px}.metadata dt{font-size:10px;color:#7b8985}.metadata dd{margin:0;color:#2d4944;font-size:13px}.feedback-card{padding:13px 0;border-top:1px solid #e8eeec}.feedback-card.reported{padding-left:10px;border-left:3px solid #c65c42}.feedback-card>div{display:flex;gap:8px;align-items:center}.feedback-card strong{color:#24423c}.feedback-card span{padding:2px 6px;border-radius:99px;background:#fff0eb;color:#9d402c;font-size:10px}.feedback-card p{margin:7px 0;color:#5f716d;font-size:13px;line-height:1.6}.feedback-card time{color:#8a9693;font-size:10px}.feedback-empty{padding:24px 0;text-align:center;color:#7e8b88;font-size:13px}@media(max-width:560px){.drawer header{padding:21px}.drawer-body{padding:16px}.risk-panel,.form-grid,.metadata dl{grid-template-columns:1fr}.form-grid .full{grid-column:auto}}
</style>
