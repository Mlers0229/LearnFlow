<template>
  <section class="catalog-panel">
    <header>
      <div><span class="eyebrow">MODEL CATALOG</span><h2>可用模型目录</h2><p>从当前提供商读取的模型。选择后仍需保存，才会成为系统默认模型。</p></div>
      <div class="catalog-actions">
        <label><span>搜索目录</span><input v-model="keyword" type="search" placeholder="输入模型名称" /></label>
        <button type="button" :disabled="syncing" @click="$emit('sync')">{{ syncing ? '同步中…' : '同步模型目录' }}</button>
      </div>
    </header>
    <div v-if="syncState" class="result" :class="syncState.tone" role="status"><strong>{{ syncState.title }}</strong><span>{{ syncState.detail }}</span></div>
    <div v-if="!filteredModels.length" class="empty"><strong>{{ models.length ? '没有匹配的模型' : '目录尚未同步' }}</strong><span>{{ models.length ? '调整搜索词后重试。' : '确认服务器凭据可用，然后执行目录同步。' }}</span></div>
    <div v-else class="catalog-grid">
      <button v-for="model in filteredModels" :key="model.id" type="button" :class="{ selected: model.id === selectedModel }" @click="$emit('select', model.id)">
        <span class="model-name">{{ model.id }}</span><span class="owner">{{ model.ownedBy || 'remote' }}</span><span class="state">{{ model.id === selectedModel ? '默认模型' : '设为默认' }}</span>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { ModelCatalogItem, OperationResult } from './modelConfig';
const props = defineProps<{ models: ModelCatalogItem[]; selectedModel: string; syncing: boolean; syncState: OperationResult | null }>();
defineEmits<{ sync: []; select: [id: string] }>();
const keyword = ref('');
const filteredModels = computed(() => { const value = keyword.value.trim().toLowerCase(); return value ? props.models.filter((model) => model.id.toLowerCase().includes(value) || String(model.ownedBy ?? '').toLowerCase().includes(value)) : props.models; });
</script>

<style scoped>
.catalog-panel{padding:22px;border:1px solid #dce7e3;border-radius:20px;background:#fff}.catalog-panel header{display:flex;align-items:flex-end;justify-content:space-between;gap:20px}.eyebrow{font-size:10px;letter-spacing:.15em;color:#71817d}.catalog-panel h2{margin:5px 0;font-size:18px;color:#1c3c36}.catalog-panel p{margin:0;color:#73827f;font-size:12px}.catalog-actions{display:flex;align-items:flex-end;gap:8px}.catalog-actions label{display:grid;gap:5px;color:#75837f;font-size:10px}.catalog-actions input{width:210px;border:1px solid #ccd9d5;border-radius:9px;padding:8px 10px;font:inherit}.catalog-actions button{border:0;border-radius:9px;padding:9px 12px;background:#176b5a;color:#fff;font-weight:700;cursor:pointer}.catalog-actions button:disabled{opacity:.55;cursor:wait}.result{display:grid;gap:3px;margin-top:15px;padding:12px 14px;border-radius:11px;font-size:12px}.result.success{background:#e9f6ef;color:#27654f}.result.error{background:#fff0ec;color:#9b4230}.result.warning{background:#fff6de;color:#876417}.empty{display:grid;place-content:center;gap:6px;min-height:170px;margin-top:16px;border:1px dashed #ccd9d5;border-radius:14px;text-align:center;color:#7d8a87}.empty strong{color:#3b5550}.catalog-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:16px}.catalog-grid button{position:relative;display:grid;gap:6px;min-width:0;padding:14px;border:1px solid #dce6e2;border-radius:13px;background:#f9fbfa;text-align:left;cursor:pointer}.catalog-grid button.selected{border-color:#3f917f;background:#edf7f2;box-shadow:inset 0 0 0 1px #3f917f}.model-name{overflow:hidden;color:#23443d;font-weight:750;text-overflow:ellipsis;white-space:nowrap}.owner{color:#7a8985;font-size:10px}.state{color:#176b5a;font-size:11px;font-weight:700}@media(max-width:900px){.catalog-grid{grid-template-columns:1fr 1fr}}@media(max-width:620px){.catalog-panel header,.catalog-actions{align-items:stretch;flex-direction:column}.catalog-actions input{width:100%}.catalog-grid{grid-template-columns:1fr}}
</style>
