<script setup lang="ts">
import { computed, ref } from 'vue'
import { reviewReason } from './resourceUploadUtils'
import type { ResourceRecord } from './types'

const props = defineProps<{ records: ResourceRecord[]; loading: boolean; busy: boolean }>()
const emit = defineEmits<{ refresh: []; retry: [record: ResourceRecord] }>()
const filter = ref('ALL')
const filtered = computed(() => filter.value === 'ALL' ? props.records : props.records.filter((record) => String(record.status || 'PENDING').toUpperCase() === filter.value))

const statusCopy: Record<string, { label: string; detail: string }> = {
  ACTIVE: { label: '已上架', detail: '审核通过，资源已经可以使用。' },
  PENDING: { label: '待审核', detail: '处理完成后将由管理员审核。' },
  INACTIVE: { label: '未通过', detail: '请查看原因并完善后重新提交。' },
}

function copyFor(record: ResourceRecord) {
  return statusCopy[String(record.status || 'PENDING').toUpperCase()] || statusCopy.PENDING
}

function dateLabel(value?: string) {
  if (!value) return '时间未知'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)
}
</script>

<template>
  <section id="my-submissions" class="submissions" aria-labelledby="submissions-heading">
    <div class="submissions-head">
      <div><span>MY SUBMISSIONS</span><h2 id="submissions-heading">我的提交</h2><p>解析状态与审核状态分别展示，失败时可直接继续处理。</p></div>
      <button type="button" class="refresh-button" :disabled="loading" @click="emit('refresh')">{{ loading ? '刷新中…' : '刷新状态' }}</button>
    </div>
    <div class="filter-row" role="group" aria-label="筛选提交状态">
      <button v-for="item in [{v:'ALL',l:'全部'},{v:'PENDING',l:'待审核'},{v:'ACTIVE',l:'已上架'},{v:'INACTIVE',l:'未通过'}]" :key="item.v" type="button" :class="{ active: filter === item.v }" @click="filter = item.v">{{ item.l }}</button>
    </div>

    <div v-if="loading && !records.length" class="empty-state">正在加载提交记录…</div>
    <div v-else-if="!filtered.length" class="empty-state">当前筛选下还没有提交记录。</div>
    <div v-else class="submission-list">
      <article v-for="record in filtered" :key="String(record.resourceId || record.id || record.title)" class="submission-card">
        <div class="record-main">
          <div class="record-tags">
            <span class="status" :class="String(record.status || 'PENDING').toLowerCase()">{{ copyFor(record).label }}</span>
            <span class="ingestion">处理：{{ record.ingestionStatus || '等待同步' }}</span>
            <span>{{ record.sourceType || 'RESOURCE' }}</span>
          </div>
          <h3>{{ record.title || '未命名资源' }}</h3>
          <p>{{ copyFor(record).detail }}</p>
          <div v-if="reviewReason(record)" class="reason"><b>原因</b><span>{{ reviewReason(record) }}</span></div>
        </div>
        <div class="record-side">
          <time>{{ dateLabel(record.updatedAt || record.createdAt) }}</time>
          <button v-if="String(record.status).toUpperCase() === 'INACTIVE' || String(record.ingestionStatus).toUpperCase() === 'FAILED'" type="button" :disabled="busy" @click="emit('retry', record)">完善并重试</button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.submissions{display:grid;gap:20px}.submissions-head{display:flex;align-items:flex-end;justify-content:space-between;gap:20px}.submissions-head span{font-size:11px;font-weight:800;letter-spacing:.14em;color:#1b7868}.submissions-head h2{margin:6px 0 5px;color:#183a34;font-size:28px;letter-spacing:-.035em}.submissions-head p{margin:0;color:#6b807b}.refresh-button,.record-side button{border:1px solid #bdd5cf;border-radius:11px;background:#fff;color:#226858;font-weight:750;padding:10px 13px;cursor:pointer}.refresh-button:disabled,.record-side button:disabled{opacity:.5;cursor:wait}.filter-row{display:flex;gap:8px;overflow:auto;padding-bottom:2px}.filter-row button{border:0;border-radius:999px;padding:8px 13px;background:#edf3f1;color:#58706a;font-weight:700;white-space:nowrap;cursor:pointer}.filter-row button.active{background:#173e36;color:#fff}.submission-list{display:grid;gap:11px}.submission-card{display:flex;justify-content:space-between;gap:18px;padding:18px;border:1px solid #dfe9e5;border-radius:17px;background:#fff}.record-main{min-width:0}.record-tags{display:flex;flex-wrap:wrap;gap:7px;align-items:center}.record-tags span{padding:4px 8px;border-radius:999px;background:#eef3f1;color:#60736f;font-size:11px;font-weight:750}.record-tags .status.active{background:#e3f5ec;color:#176b52}.record-tags .status.pending{background:#fff3d8;color:#7c5b11}.record-tags .status.inactive{background:#fce7e2;color:#944435}.submission-card h3{margin:11px 0 6px;color:#20423b;font-size:17px}.submission-card p{margin:0;color:#6a7e79;font-size:13px}.reason{display:flex;gap:9px;margin-top:12px;padding:10px 11px;background:#fff3ef;border-radius:10px;color:#874435;font-size:13px}.record-side{flex:0 0 auto;display:flex;flex-direction:column;align-items:flex-end;justify-content:space-between;gap:14px}.record-side time{color:#7b8d89;font-size:12px}.empty-state{padding:42px 20px;text-align:center;border:1px dashed #cfddd9;border-radius:17px;color:#71847f;background:#f8fbfa}@media(max-width:680px){.submissions-head{align-items:flex-start}.submissions-head p{font-size:13px;max-width:230px}.submission-card{flex-direction:column}.record-side{flex-direction:row;align-items:center}.refresh-button{flex:0 0 auto}}
</style>
