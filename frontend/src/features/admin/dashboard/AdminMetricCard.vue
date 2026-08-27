<script setup lang="ts">
import { ArrowUpRight } from 'lucide-vue-next'
import type { DashboardMetric } from './types'
defineProps<{ metric: DashboardMetric; loading: boolean }>()
</script>

<template>
  <div v-if="loading" class="metric-card metric-card--loading" aria-hidden="true"><i /><i /><i /></div>
  <RouterLink v-else-if="metric.to" :to="{ path: metric.to, query: metric.query }" :class="['metric-card', `is-${metric.tone}`]">
    <span class="metric-icon"><component :is="metric.icon" :size="19" /></span>
    <span class="metric-label">{{ metric.label }}</span><strong>{{ metric.value }}</strong><small>{{ metric.detail }}</small>
    <span class="metric-note">{{ metric.note }} <ArrowUpRight :size="13" /></span>
  </RouterLink>
  <article v-else :class="['metric-card', `is-${metric.tone}`]">
    <span class="metric-icon"><component :is="metric.icon" :size="19" /></span>
    <span class="metric-label">{{ metric.label }}</span><strong>{{ metric.value }}</strong><small>{{ metric.detail }}</small>
    <span class="metric-note">{{ metric.note }}</span>
  </article>
</template>

<style scoped>
.metric-card{position:relative;display:grid;min-height:168px;box-sizing:border-box;padding:19px;border:1px solid #dce5e2;border-radius:18px;background:#fff;color:#193b35;text-decoration:none;transition:.18s ease}.metric-card[href]:hover,.metric-card[href]:focus-visible{transform:translateY(-2px);border-color:#72a99a;box-shadow:0 15px 30px rgba(33,64,55,.09);outline:none}.metric-card:before{content:"";position:absolute;inset:0 0 auto;height:3px;border-radius:18px 18px 0 0;background:#9db7af}.metric-card.is-good:before{background:#299374}.metric-card.is-warning:before{background:#d29a43}.metric-card.is-danger:before{background:#bd5b4e}.metric-icon{display:grid;width:36px;height:36px;place-items:center;border-radius:11px;background:#edf4f1;color:#247460}.metric-label{margin-top:14px;color:#70837d;font-size:10px;font-weight:800;letter-spacing:.08em}.metric-card>strong{margin-top:4px;font-family:Georgia,serif;font-size:30px;font-weight:500;line-height:1}.metric-card>small{margin-top:7px;color:#5f756e;font-size:11px}.metric-note{display:flex;align-items:center;gap:4px;align-self:end;margin-top:16px;color:#84928e;font-size:9px}.metric-card--loading{gap:11px}.metric-card--loading i{display:block;height:14px;border-radius:6px;background:linear-gradient(90deg,#edf1ef,#f8faf9,#edf1ef);background-size:200% 100%;animation:shine 1.4s infinite}.metric-card--loading i:first-child{width:36px;height:36px}.metric-card--loading i:nth-child(2){width:48%}.metric-card--loading i:last-child{width:76%}@keyframes shine{to{background-position:-200% 0}}
</style>
