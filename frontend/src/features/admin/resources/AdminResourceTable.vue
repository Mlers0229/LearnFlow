<template>
  <div class="table-shell" aria-live="polite">
    <div v-if="loading" class="table-state">正在载入资源队列…</div>
    <div v-else-if="!resources.length" class="table-state">
      <strong>没有符合条件的资源</strong>
      <span>调整筛选条件，或录入一条新的学习资源。</span>
    </div>
    <div v-else class="table-scroll">
      <table>
        <thead>
          <tr>
            <th class="check-cell"><input type="checkbox" :checked="allSelected" :aria-label="allSelected ? '取消选择全部资源' : '选择全部资源'" @change="toggleAll" /></th>
            <th>资源</th>
            <th>分类</th>
            <th>质量与风险</th>
            <th>摄取</th>
            <th>状态</th>
            <th class="action-head">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="resource in resources" :key="resource.id" :class="{ risky: resource.invalidReportCount > 0, selected: selectedIds.includes(resource.id) }">
            <td class="check-cell"><input type="checkbox" :checked="selectedIds.includes(resource.id)" :aria-label="`选择资源：${resource.title}`" @change="$emit('toggle', resource.id)" /></td>
            <td class="resource-cell">
              <button class="title-button" type="button" @click="$emit('open', resource)">{{ resource.title }}</button>
              <div class="resource-meta">#{{ resource.id }} · {{ host(resource.url) || resource.sourceType || '未知来源' }}</div>
              <div class="tag-list">
                <span v-for="tag in splitTags(resource.tags).slice(0, 3)" :key="tag">{{ tag }}</span>
              </div>
            </td>
            <td>
              <div class="primary-text">{{ domainText(resource.domain) }}</div>
              <div class="secondary-text">{{ levelText(resource.level) }} · {{ formatDuration(resource.durationMinutes) }}</div>
            </td>
            <td>
              <div v-if="resource.invalidReportCount > 0" class="risk-badge">{{ resource.invalidReportCount }} 次无效举报</div>
              <div class="primary-text">{{ resource.avgRating == null ? '暂无评分' : `${Number(resource.avgRating).toFixed(1)} 分` }}</div>
              <div class="secondary-text">{{ resource.feedbackCount }} 条反馈</div>
            </td>
            <td>
              <div class="primary-text">{{ ingestionText(resource.ingestionStatus) }}</div>
              <div class="secondary-text">{{ resource.currentIngestionId ? `任务 #${resource.currentIngestionId}` : '暂无任务' }}</div>
            </td>
            <td><span class="status-badge" :class="normalizeStatus(resource.status).toLowerCase()">{{ statusText(resource.status) }}</span></td>
            <td>
              <div class="row-actions">
                <button type="button" @click="$emit('open', resource)">查看详情</button>
                <button v-if="normalizeStatus(resource.status) === 'ACTIVE'" type="button" @click="$emit('status', resource, 'INACTIVE')">下线</button>
                <button v-else-if="needsReingestion(resource)" type="button" class="primary-action" @click="$emit('reingest', resource)">重新摄取</button>
                <button v-else-if="isIngestionPending(resource)" type="button" disabled>摄取中</button>
                <button v-else type="button" class="primary-action" @click="$emit('status', resource, 'ACTIVE')">上线</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { domainText, formatDuration, levelText, normalizeStatus, splitTags, statusText, type ManagedResource, type ResourceStatus } from './resourceManagement';

const props = defineProps<{ resources: ManagedResource[]; selectedIds: number[]; loading: boolean }>();
const emit = defineEmits<{
  toggle: [id: number];
  'toggle-all': [ids: number[]];
  open: [resource: ManagedResource];
  status: [resource: ManagedResource, status: ResourceStatus];
  reingest: [resource: ManagedResource];
}>();

const allSelected = computed(() => props.resources.length > 0 && props.resources.every((item) => props.selectedIds.includes(item.id)));

function toggleAll() {
  emit('toggle-all', allSelected.value ? [] : props.resources.map((item) => item.id));
}

function host(url?: string | null) {
  if (!url) return '';
  try { return new URL(url).hostname.replace(/^www\./, ''); } catch { return ''; }
}

function ingestionText(status?: string | null) {
  const labels: Record<string, string> = { SUCCEEDED: '摄取成功', RUNNING: '摄取中', PROCESSING: '摄取中', PENDING: '等待摄取', FAILED: '摄取失败', NOT_STARTED: '尚未摄取' };
  return labels[String(status ?? '')] ?? (status || '尚未摄取');
}

function needsReingestion(resource: ManagedResource) {
  return resource.ingestionStatus === 'FAILED' && Boolean(resource.url);
}

function isIngestionPending(resource: ManagedResource) {
  return ['PENDING', 'PROCESSING', 'RUNNING'].includes(String(resource.ingestionStatus ?? ''));
}
</script>

<style scoped>
.table-shell{min-height:240px}.table-scroll{overflow-x:auto;border:1px solid #dfe8e5;border-radius:16px;background:#fff}.table-state{min-height:240px;display:grid;place-content:center;text-align:center;gap:8px;color:#6d7c82;background:#fafcfb;border:1px dashed #ccd9d5;border-radius:16px}.table-state strong{font-size:16px;color:#203b38}table{width:100%;min-width:1080px;border-collapse:collapse}th{padding:12px 14px;text-align:left;font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:#6c7d7a;background:#f3f7f5;border-bottom:1px solid #dfe8e5}td{padding:15px 14px;border-bottom:1px solid #e8efec;vertical-align:middle}tbody tr:last-child td{border-bottom:0}tbody tr{transition:background .16s ease}tbody tr:hover,tbody tr.selected{background:#f5faf7}tbody tr.risky{box-shadow:inset 3px 0 #c65c42}.check-cell{width:42px;text-align:center}.check-cell input{width:16px;height:16px;accent-color:#176b5a}.resource-cell{min-width:260px;max-width:360px}.title-button{display:block;max-width:100%;padding:0;border:0;background:transparent;color:#143d36;font:inherit;font-weight:750;text-align:left;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;cursor:pointer}.title-button:hover{text-decoration:underline}.resource-meta,.secondary-text{margin-top:5px;color:#74827f;font-size:12px}.primary-text{font-size:13px;color:#29423f}.tag-list{display:flex;gap:5px;margin-top:8px}.tag-list span{padding:2px 7px;border-radius:999px;background:#edf4f1;color:#496760;font-size:10px}.risk-badge{display:inline-flex;margin-bottom:5px;padding:3px 8px;border-radius:999px;background:#fff0eb;color:#9b3e2b;font-size:11px;font-weight:700}.status-badge{display:inline-flex;padding:5px 9px;border-radius:999px;font-size:11px;font-weight:750}.status-badge.active{background:#e3f5ed;color:#176448}.status-badge.pending{background:#fff4d9;color:#8b6514}.status-badge.inactive{background:#edf0ef;color:#63716e}.action-head{text-align:right}.row-actions{display:flex;justify-content:flex-end;gap:5px}.row-actions button{border:0;border-radius:8px;padding:6px 9px;background:#edf3f1;color:#3c5a54;font-size:11px;cursor:pointer}.row-actions button:disabled{cursor:not-allowed;opacity:.55}.row-actions .primary-action{background:#176b5a;color:#fff}@media(max-width:720px){th,td{padding:12px 10px}}
</style>
