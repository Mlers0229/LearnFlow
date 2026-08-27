<template>
  <main class="model-page">
    <header class="page-header">
      <div><span class="eyebrow">ADMIN · MODEL CONTROL</span><h1>模型配置</h1><p>管理 OpenAI 兼容提供商、服务器凭据状态、模型目录和 Agent 策略。浏览器不会接收或保存完整 API Key。</p></div>
      <div class="header-status" :class="config?.configured ? 'ready' : 'waiting'"><span>{{ config?.configured ? 'Provider ready' : 'Setup required' }}</span><strong>{{ provider }}</strong><small>{{ models.length }} 个目录模型 · {{ form.defaultModel || '未选择默认模型' }}</small></div>
    </header>

    <div v-if="error" class="page-error" role="alert"><span>{{ error }}</span><button type="button" @click="load">重新加载</button></div>
    <div v-if="loading" class="loading-state">正在读取服务器模型配置…</div>

    <template v-else>
      <div class="page-grid">
        <form class="config-panel" @submit.prevent="save">
          <header class="section-heading"><div><span class="eyebrow">PROVIDER & CREDENTIAL</span><h2>提供商与凭据</h2></div><span class="section-badge">{{ provider }}</span></header>

          <label class="field"><span>API Base</span><input v-model="form.apiBase" type="url" placeholder="https://api.example.com" /><small>填写兼容 OpenAI API 的服务根地址，系统会请求其 `/v1/models` 和 `/v1/chat/completions`。</small></label>

          <section class="credential-card" :class="config?.hasApiKey ? 'configured' : 'missing'">
            <div class="credential-icon">KEY</div>
            <div><span>服务器 API Key</span><strong>{{ config?.maskedApiKey || '尚未配置' }}</strong><p>密钥只允许通过部署平台 Secret 或环境变量 <code>LLM_API_KEY</code> 更新，管理页面永不回显完整内容。</p></div>
            <span class="credential-state">{{ config?.hasApiKey ? '已注入' : '缺失' }}</span>
          </section>

          <div class="connection-row">
            <div><strong>测试当前生效连接</strong><span>使用服务器已有 Secret 探测远端目录，不会发送浏览器数据。</span></div>
            <button type="button" :disabled="testing" @click="testConnection">{{ testing ? '测试中…' : '测试连接' }}</button>
          </div>
          <div v-if="connectionState" class="operation-result" :class="connectionState.tone" role="status"><strong>{{ connectionState.title }}</strong><span>{{ connectionState.detail }}</span></div>

          <section class="configuration-section">
            <div class="section-heading"><div><span class="eyebrow">DEFAULT ROUTING</span><h2>默认模型</h2></div><span class="section-badge">系统统一分配</span></div>
            <label class="field"><span>模型 ID</span><input v-model="form.defaultModel" list="available-models" placeholder="例如：deepseek-chat" /><datalist id="available-models"><option v-for="model in models" :key="model.id" :value="model.id" /></datalist><small>可手动填写，也可以从下方目录选择。新聊天和新计划任务会读取保存后的默认模型。</small></label>
          </section>

          <section class="configuration-section">
            <div class="section-heading"><div><span class="eyebrow">AGENT POLICY</span><h2>Agent 与目录策略</h2></div></div>
            <label class="switch-card"><div><strong>自动同步模型目录</strong><span>允许管理员按需从提供商 `/v1/models` 获取目录。</span></div><input v-model="form.autoDiscoverModels" type="checkbox" role="switch" /></label>
            <label class="switch-card"><div><strong>启用 LLM 计划生成</strong><span>关闭后 PlanAgent 回退到规则计划；聊天服务不受此开关影响。</span></div><input v-model="form.enableLlmPlan" type="checkbox" role="switch" /></label>
          </section>

          <footer class="save-bar" :class="saveTone">
            <div><strong>{{ saveTitle }}</strong><span>{{ saveDetail }}</span></div>
            <div><button v-if="dirty" type="button" class="reset-button" :disabled="saving" @click="reset">放弃更改</button><button class="save-button" type="submit" :disabled="!dirty || saving">{{ saving ? '保存中…' : '保存配置' }}</button></div>
          </footer>
        </form>

        <AdminModelRuntimePanel :config="config" />
      </div>

      <AdminModelCatalog :models="models" :selected-model="form.defaultModel" :syncing="syncing" :sync-state="syncState" @sync="syncCatalog" @select="pickModel" />
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import AdminModelCatalog from '../features/admin/models/AdminModelCatalog.vue';
import AdminModelRuntimePanel from '../features/admin/models/AdminModelRuntimePanel.vue';
import { providerLabel } from '../features/admin/models/modelConfig';
import { useAdminModelConfig } from '../features/admin/models/useAdminModelConfig';

const { config, form, loading, saving, syncing, testing, error, saveState, syncState, connectionState, dirty, models, load, save, syncCatalog, testConnection, reset, pickModel } = useAdminModelConfig();
const provider = computed(() => providerLabel(form.apiBase || config.value?.apiBase));
const saveTone = computed(() => saving.value ? 'busy' : saveState.value === 'error' ? 'error' : saveState.value === 'saved' ? 'success' : dirty.value ? 'dirty' : 'clean');
const saveTitle = computed(() => saving.value ? '正在保存配置' : saveState.value === 'error' ? '保存失败' : saveState.value === 'saved' ? '配置已保存' : dirty.value ? '存在未保存更改' : '当前配置已同步');
const saveDetail = computed(() => dirty.value ? '保存后立即作用于新的聊天与计划任务。' : 'API Base、默认模型与策略开关无需重启。');
watch(dirty, (value) => { if (value) saveState.value = 'idle'; });
onMounted(load);
</script>

<style scoped>
.model-page{display:flex;flex-direction:column;gap:16px;padding:2px 0 36px;color:#203b36}.page-header{display:grid;grid-template-columns:minmax(0,1fr) 330px;gap:24px;padding:27px 29px;border:1px solid #dce7e3;border-radius:24px;background:radial-gradient(circle at 85% 10%,rgba(208,235,223,.95),transparent 30%),linear-gradient(135deg,#fbfdfc,#eff7f3)}.eyebrow{font-size:10px;letter-spacing:.16em;color:#71817d}.page-header h1{margin:7px 0;font:700 33px/1.1 Georgia,'Times New Roman',serif;color:#173d36}.page-header p{max-width:760px;margin:0;color:#637570;font-size:13px;line-height:1.75}.header-status{display:grid;align-content:center;gap:5px;padding:18px;border-radius:17px}.header-status.ready{background:#173f38;color:#fff}.header-status.waiting{background:#fff3d9;color:#674d18}.header-status span{font-size:10px;letter-spacing:.1em;text-transform:uppercase;opacity:.7}.header-status strong{font-size:23px}.header-status small{opacity:.75}.page-grid{display:grid;grid-template-columns:minmax(0,1.3fr) minmax(330px,.7fr);gap:14px;align-items:start}.config-panel{padding:22px;border:1px solid #dce7e3;border-radius:20px;background:#fff}.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.section-heading h2{margin:5px 0 0;font-size:18px;color:#1d3d36}.section-badge{padding:5px 9px;border-radius:99px;background:#edf5f2;color:#406058;font-size:10px}.field{display:grid;gap:7px;margin-top:17px;color:#526a64;font-size:11px}.field>span{font-weight:700}.field input{border:1px solid #cbd9d4;border-radius:10px;padding:11px 12px;background:#fbfcfc;color:#1d3c36;font:inherit;font-size:13px}.field small{color:#7c8986;line-height:1.55}.credential-card{display:grid;grid-template-columns:42px minmax(0,1fr) auto;gap:12px;align-items:center;margin-top:16px;padding:15px;border:1px solid;border-radius:14px}.credential-card.configured{border-color:#cee5da;background:#edf7f2}.credential-card.missing{border-color:#edddbc;background:#fff7e5}.credential-icon{display:grid;place-items:center;width:42px;height:42px;border-radius:12px;background:#173f38;color:#fff;font-size:10px;font-weight:800}.credential-card>div:nth-child(2){display:grid;gap:3px}.credential-card span{font-size:10px;color:#70817c}.credential-card strong{font-family:monospace;color:#26463f;letter-spacing:.05em}.credential-card p{margin:3px 0 0;color:#697a76;font-size:10px;line-height:1.55}.credential-state{padding:4px 8px;border-radius:99px;background:#fff;font-weight:700}.connection-row{display:flex;align-items:center;justify-content:space-between;gap:15px;margin-top:14px;padding:14px;border-radius:13px;background:#f3f7f5}.connection-row>div{display:grid;gap:4px}.connection-row strong{font-size:12px}.connection-row span{color:#71817d;font-size:10px}.connection-row button{flex-shrink:0;border:0;border-radius:9px;padding:9px 13px;background:#e1eee9;color:#315c52;font-weight:700;cursor:pointer}.connection-row button:disabled{opacity:.5}.operation-result{display:grid;gap:3px;margin-top:9px;padding:11px 13px;border-radius:10px;font-size:11px}.operation-result.success{background:#e9f6ef;color:#27654f}.operation-result.error{background:#fff0ec;color:#9b4230}.operation-result.warning{background:#fff6de;color:#876417}.configuration-section{margin-top:21px;padding-top:19px;border-top:1px solid #e3ebe8}.switch-card{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-top:10px;padding:14px;border:1px solid #dce7e3;border-radius:13px;background:#fafcfb}.switch-card>div{display:grid;gap:4px}.switch-card strong{font-size:12px}.switch-card span{color:#73827f;font-size:10px}.switch-card input{width:38px;height:21px;accent-color:#176b5a;cursor:pointer}.save-bar{position:sticky;bottom:12px;z-index:2;display:flex;align-items:center;justify-content:space-between;gap:15px;margin-top:20px;padding:13px 14px;border:1px solid #d9e5e1;border-radius:13px;background:rgba(247,250,249,.96);box-shadow:0 8px 24px rgba(27,56,50,.1);backdrop-filter:blur(8px)}.save-bar>div:first-child{display:grid;gap:3px}.save-bar strong{font-size:12px}.save-bar span{color:#70807c;font-size:10px}.save-bar>div:last-child{display:flex;gap:7px}.save-bar button{border:0;border-radius:9px;padding:9px 13px;font-weight:700;cursor:pointer}.save-button{background:#176b5a;color:#fff}.save-button:disabled{opacity:.45;cursor:not-allowed}.reset-button{background:#e8efed;color:#4a625d}.save-bar.dirty{border-color:#dccb9e;background:#fffaf0}.save-bar.success{border-color:#b9dfcf;background:#eff9f4}.save-bar.error{border-color:#efc9bd;background:#fff3ef}.page-error{display:flex;justify-content:space-between;align-items:center;padding:12px 14px;border-radius:12px;background:#fff0ec;color:#98402e;font-size:12px}.page-error button{border:0;background:transparent;color:inherit;font-weight:700;cursor:pointer}.loading-state{display:grid;place-items:center;min-height:350px;border:1px dashed #cdd9d5;border-radius:20px;background:#fafcfb;color:#768682}@media(max-width:1050px){.page-grid{grid-template-columns:1fr}.page-header{grid-template-columns:1fr 280px}}@media(max-width:720px){.page-header{grid-template-columns:1fr;padding:22px}.page-header h1{font-size:29px}.page-grid{display:block}.config-panel{padding:16px}.runtime-panel{margin-top:14px}.connection-row,.save-bar{align-items:flex-start;flex-direction:column}.save-bar>div:last-child{width:100%}.save-bar button{flex:1}.credential-card{grid-template-columns:42px minmax(0,1fr)}.credential-state{grid-column:2}.catalog-panel{margin-top:14px}}
</style>
