<template>
  <Teleport to="body">
    <div v-if="log" class="drawer-layer" role="dialog" aria-modal="true" aria-labelledby="log-detail-title">
      <button class="drawer-backdrop" type="button" aria-label="关闭调用详情" @click="$emit('close')" />
      <aside class="drawer-panel">
        <header><div><span>TRACE DETAIL</span><h2 id="log-detail-title">{{ log.agentName || 'Agent 调用' }}</h2><p>{{ formatDate(log.createdAt) }} · {{ log.durationMs ?? 0 }} ms</p></div><button type="button" aria-label="关闭" @click="$emit('close')">×</button></header>
        <section class="trace-card"><span>Trace ID</span><strong>{{ log.traceId || '未提供' }}</strong><button v-if="log.traceId" type="button" @click="copy(log.traceId)">{{ copied ? '已复制' : '复制' }}</button></section>
        <dl><div><dt>状态</dt><dd><span class="status" :class="logStatus(log).toLowerCase()">{{ statusLabel[logStatus(log)] }}</span></dd></div><div><dt>模型</dt><dd>{{ log.modelName || '未使用模型' }}</dd></div><div><dt>日志 ID</dt><dd>#{{ log.id }}</dd></div><div><dt>关联任务</dt><dd>{{ taskId || '摘要中未发现任务标识' }}</dd></div></dl>
        <section><div class="section-head"><h3>请求摘要</h3><small>已由服务端脱敏并限长</small></div><pre>{{ prettyPayload(log.requestPayload) }}</pre></section>
        <section><div class="section-head"><h3>响应摘要</h3><small>不展示完整 Prompt 或回答</small></div><pre>{{ prettyPayload(log.responsePayload) }}</pre></section>
        <footer><RouterLink v-if="taskId" :to="{ name: 'plan-generator', query: { taskId } }">查看关联任务</RouterLink><button type="button" @click="$emit('close')">关闭</button></footer>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { extractTaskId, logStatus, prettyPayload, statusLabel, type AgentLogRecord } from './agentLog'

const props = defineProps<{ log: AgentLogRecord | null }>()
defineEmits<{ close: [] }>()
const copied = ref(false)
const taskId = computed(() => props.log ? extractTaskId(props.log) : '')
function formatDate(value?: string | null) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '时间未知' }
async function copy(value: string) { await navigator.clipboard.writeText(value); copied.value = true; setTimeout(() => { copied.value = false }, 1500) }
</script>

<style scoped>
.drawer-layer{position:fixed;inset:0;z-index:4500}.drawer-backdrop{position:absolute;inset:0;border:0;background:rgba(7,25,23,.52)}.drawer-panel{position:absolute;inset:0 0 0 auto;display:flex;width:min(620px,94vw);flex-direction:column;gap:17px;overflow:auto;padding:23px;background:#f8faf9;box-shadow:-24px 0 70px rgba(0,0,0,.2)}header{display:flex;justify-content:space-between;gap:20px;padding-bottom:16px;border-bottom:1px solid #dce7e3}header span{color:#8a5a27;font-size:9px;letter-spacing:.15em}header h2{margin:5px 0;font-size:23px;color:#173d36}header p{margin:0;color:#70807c;font-size:11px}header>button{align-self:start;border:0;background:transparent;color:#62736f;font-size:28px;cursor:pointer}.trace-card{display:grid;grid-template-columns:1fr auto;gap:5px 10px;padding:14px;border-radius:13px;background:#173f38;color:#fff}.trace-card span{grid-column:1/-1;color:#a9cec3;font-size:9px;letter-spacing:.1em}.trace-card strong{overflow:hidden;font:12px ui-monospace,monospace;text-overflow:ellipsis}.trace-card button{border:0;border-radius:7px;background:#d8ebe5;color:#174c40;font-weight:700;cursor:pointer}dl{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin:0}dl>div{padding:12px;border:1px solid #dce6e3;border-radius:11px;background:#fff}dt{color:#788783;font-size:9px;text-transform:uppercase}dd{margin:4px 0 0;color:#29443e;font-size:12px}.status{display:inline-flex;padding:3px 7px;border-radius:99px;font-size:10px;font-weight:750}.status.success{background:#e4f5ed;color:#176449}.status.slow{background:#fff3d9;color:#8d6513}.status.failed{background:#fff0eb;color:#9d402f}.section-head{display:flex;justify-content:space-between;gap:12px;align-items:baseline}.section-head h3{margin:0;font-size:14px}.section-head small{color:#81908c;font-size:9px}pre{max-height:240px;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;padding:14px;border:1px solid #dbe5e2;border-radius:11px;background:#fff;color:#314b45;font:11px/1.65 ui-monospace,monospace}footer{display:flex;justify-content:flex-end;gap:8px;margin-top:auto;padding-top:10px}footer a,footer button{border:0;border-radius:9px;padding:9px 13px;background:#e4efeb;color:#315c52;font:inherit;font-size:11px;font-weight:750;text-decoration:none;cursor:pointer}footer button{background:#173f38;color:#fff}@media(max-width:560px){.drawer-panel{width:100%;box-sizing:border-box;padding:17px}dl{grid-template-columns:1fr}}
</style>
