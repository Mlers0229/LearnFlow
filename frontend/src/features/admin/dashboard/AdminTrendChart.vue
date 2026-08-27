<script setup lang="ts">
import { computed } from 'vue'
import type { TrendSeries } from './types'

const props = defineProps<{ series: TrendSeries[] }>()
const decorated = computed(() => props.series.map((item) => {
  const max = Math.max(1, ...item.points.map((point) => point.value))
  return { ...item, points: item.points.map((point) => ({ ...point, height: Math.max(5, Math.round((point.value / max) * 100)) })) }
}))
</script>

<template>
  <div class="trend-charts">
    <article v-for="item in decorated" :key="item.key" class="trend-chart">
      <header><strong>{{ item.title }}</strong><span>{{ item.summary }}</span></header>
      <div v-if="item.points.length" class="bars" role="img" :aria-label="item.summary">
        <div v-for="point in item.points" :key="`${point.label}-${point.value}`" class="bar-column">
          <span class="bar-value">{{ point.value }}</span><i :style="{ height: `${point.height}%`, background: item.color }" /><small>{{ point.label }}</small>
        </div>
      </div>
      <p v-else class="chart-empty">当前窗口没有可绘制的数据。</p>
      <ul class="visually-hidden"><li v-for="point in item.points" :key="point.label">{{ point.label }}：{{ point.value }}</li></ul>
    </article>
  </div>
</template>

<style scoped>
.trend-charts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.trend-chart{min-width:0;padding:16px;border:1px solid #dfe7e4;border-radius:16px;background:#fbfcfb}.trend-chart header{display:grid;gap:4px}.trend-chart header strong{font-size:13px}.trend-chart header span{color:#73857f;font-size:9px;line-height:1.45}.bars{display:flex;height:135px;align-items:end;gap:7px;margin-top:18px;padding-top:18px;border-bottom:1px solid #dce4e1}.bar-column{position:relative;display:flex;height:100%;min-width:0;flex:1;align-items:center;justify-content:flex-end;flex-direction:column}.bar-column i{width:min(22px,72%);min-height:4px;border-radius:5px 5px 0 0}.bar-value{position:absolute;top:-14px;color:#516760;font-size:8px}.bar-column small{overflow:hidden;width:100%;margin-top:5px;color:#80908b;font-size:7px;text-align:center;text-overflow:ellipsis;white-space:nowrap}.chart-empty{display:grid;height:135px;place-items:center;margin:18px 0 0;color:#81918c;font-size:10px}.visually-hidden{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}@media(max-width:900px){.trend-charts{grid-template-columns:1fr}}
</style>
