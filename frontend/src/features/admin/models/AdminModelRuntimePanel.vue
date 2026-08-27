<template>
  <aside class="runtime-panel">
    <header><span class="eyebrow">RUNTIME SNAPSHOT</span><h2>当前生效配置</h2><p>这里展示服务器实际使用的值与来源，不展示完整密钥。</p></header>
    <div class="runtime-status" :class="config?.configured ? 'ready' : 'waiting'"><span>{{ config?.configured ? '配置已就绪' : '等待完整配置' }}</span><strong>{{ provider }}</strong><small>{{ config?.configured ? '聊天与计划服务可读取凭据' : '请检查 API Base 与部署 Secret' }}</small></div>
    <dl>
      <div><dt>API Base</dt><dd>{{ config?.apiBase || '未配置' }}</dd><small>{{ source(config?.source?.apiBase) }}</small></div>
      <div><dt>API Key</dt><dd class="secret">{{ config?.maskedApiKey || '未配置' }}</dd><small>{{ source(config?.source?.apiKey) }}</small></div>
      <div><dt>默认模型</dt><dd>{{ config?.defaultModel || '未配置' }}</dd><small>{{ source(config?.source?.defaultModel) }}</small></div>
      <div><dt>最近保存</dt><dd>{{ updatedAt }}</dd><small>运行时配置更新时间</small></div>
    </dl>
    <section class="impact">
      <h3>生效范围</h3>
      <div><span>聊天回答</span><strong>立即生效</strong></div>
      <div><span>计划 Agent</span><strong>新任务生效</strong></div>
      <div><span>API Key Secret</span><strong>需部署平台更新</strong></div>
      <p>保存 API Base、默认模型和开关无需重启。更新 `LLM_API_KEY` 后是否重启取决于部署平台的 Secret 注入方式。</p>
    </section>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { formatConfigSource, providerLabel, type AdminModelConfig } from './modelConfig';
const props = defineProps<{ config: AdminModelConfig | null }>();
const provider = computed(() => providerLabel(props.config?.apiBase));
const updatedAt = computed(() => { const value = props.config?.updatedAt; if (!value) return '尚未通过管理端保存'; const date = new Date(value); return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(date); });
const source = formatConfigSource;
</script>

<style scoped>
.runtime-panel{padding:21px;border:1px solid #dbe6e2;border-radius:20px;background:#fff}.eyebrow{font-size:10px;letter-spacing:.15em;color:#71817d}.runtime-panel h2{margin:5px 0;font-size:18px;color:#1d3d36}.runtime-panel header p{margin:0;color:#73827f;font-size:12px;line-height:1.6}.runtime-status{display:grid;gap:5px;margin:17px 0;padding:16px;border-radius:15px}.runtime-status.ready{background:#173f38;color:#fff}.runtime-status.waiting{background:#fff4df;color:#674e1b}.runtime-status span{font-size:10px;opacity:.72}.runtime-status strong{font-size:22px}.runtime-status small{opacity:.78}.runtime-panel dl{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin:0}.runtime-panel dl div{min-width:0;padding:12px;border:1px solid #e1e9e6;border-radius:12px;background:#fafcfb}.runtime-panel dt{color:#788783;font-size:10px}.runtime-panel dd{overflow:hidden;margin:6px 0;color:#294640;font-size:12px;font-weight:700;text-overflow:ellipsis;white-space:nowrap}.runtime-panel dd.secret{font-family:monospace;letter-spacing:.06em}.runtime-panel dl small{color:#8b9794;font-size:9px}.impact{margin-top:15px;padding:15px;border-radius:14px;background:#f1f6f4}.impact h3{margin:0 0 10px;font-size:13px}.impact div{display:flex;justify-content:space-between;padding:7px 0;border-bottom:1px solid #dce6e2;font-size:11px}.impact div strong{color:#176b5a}.impact p{margin:11px 0 0;color:#687a76;font-size:10px;line-height:1.6}@media(max-width:460px){.runtime-panel dl{grid-template-columns:1fr}}
</style>
