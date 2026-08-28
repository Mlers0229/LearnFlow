<template>
  <Teleport to="body">
    <div v-if="state.visible" class="resource-viewer-layer" role="dialog" aria-modal="true" aria-labelledby="resource-viewer-title">
      <button type="button" class="resource-viewer-backdrop" aria-label="关闭资源查看器" @click="closeViewer" />
      <section class="resource-viewer-panel">
        <header>
          <div>
            <span>RESOURCE VIEWER</span>
            <h2 id="resource-viewer-title">{{ state.title }}</h2>
            <p v-if="state.filename">{{ state.filename }}<template v-if="state.contentType"> · {{ state.contentType }}</template></p>
          </div>
          <button type="button" class="resource-viewer-close" aria-label="关闭" @click="closeViewer">×</button>
        </header>

        <main>
          <div v-if="loading" class="resource-viewer-state" role="status">
            <i />
            <strong>正在安全读取资源原件</strong>
            <p>仅当前账号有权限时才会返回内容。</p>
          </div>
          <div v-else-if="state.mode === 'ERROR'" class="resource-viewer-state error" role="alert">
            <strong>无法查看这份资源</strong>
            <p>{{ state.error }}</p>
          </div>
          <pre v-else-if="state.mode === 'TEXT'" class="resource-viewer-text">{{ state.text }}</pre>
          <iframe v-else-if="state.mode === 'PDF'" class="resource-viewer-pdf" :src="state.objectUrl" title="PDF 资源预览" />
        </main>

        <footer>
          <span>文本始终按纯文本渲染，不执行其中的 HTML 或脚本。</span>
          <div>
            <button v-if="state.blob" type="button" class="resource-viewer-download" @click="downloadCurrent">下载原件</button>
            <button type="button" class="resource-viewer-done" @click="closeViewer">关闭</button>
          </div>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useResourceViewer } from './useResourceViewer'

const { state, loading, closeViewer, downloadCurrent } = useResourceViewer()

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && state.visible) closeViewer()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.resource-viewer-layer{position:fixed;inset:0;z-index:5000;display:grid;place-items:center;padding:clamp(12px,3vw,30px)}.resource-viewer-backdrop{position:absolute;inset:0;border:0;background:rgba(11,30,27,.68);backdrop-filter:blur(5px)}.resource-viewer-panel{position:relative;display:grid;grid-template-rows:auto minmax(0,1fr) auto;width:min(1100px,100%);height:min(820px,94vh);overflow:hidden;border:1px solid rgba(255,255,255,.22);border-radius:24px;background:#f8faf9;box-shadow:0 32px 100px rgba(0,0,0,.35)}header{display:flex;justify-content:space-between;gap:20px;padding:20px 24px;background:#173f37;color:#fff}header span{font-size:9px;font-weight:850;letter-spacing:.16em;color:#9fcfc1}header h2{margin:6px 0 0;font-size:20px;line-height:1.35}header p{margin:5px 0 0;color:#bed2cc;font-size:11px}.resource-viewer-close{align-self:start;border:0;background:transparent;color:#d7e7e2;font-size:30px;cursor:pointer}main{min-height:0;overflow:auto;background:#eef3f1}.resource-viewer-text{box-sizing:border-box;min-height:100%;margin:0;padding:26px;white-space:pre-wrap;overflow-wrap:anywhere;background:#fff;color:#263f3a;font:14px/1.85 ui-monospace,SFMono-Regular,Consolas,monospace}.resource-viewer-pdf{display:block;width:100%;height:100%;min-height:540px;border:0;background:#fff}.resource-viewer-state{min-height:100%;display:grid;place-content:center;justify-items:center;gap:8px;padding:30px;text-align:center;color:#49635d}.resource-viewer-state i{width:28px;height:28px;border:3px solid #c7ddd6;border-top-color:#197b67;border-radius:50%;animation:viewer-spin .8s linear infinite}.resource-viewer-state strong{color:#24463f}.resource-viewer-state p{margin:0;font-size:12px}.resource-viewer-state.error{background:#fff5f2;color:#8d4a3c}.resource-viewer-state.error strong{color:#9c3f30}footer{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:13px 18px;border-top:1px solid #dce7e3;background:#fff}footer>span{color:#71817d;font-size:10px}footer>div{display:flex;gap:8px}footer button{border-radius:9px;padding:8px 12px;font:inherit;font-size:12px;font-weight:750;cursor:pointer}.resource-viewer-download{border:1px solid #b8d1ca;background:#edf6f3;color:#216b5a}.resource-viewer-done{border:0;background:#173f37;color:#fff}@keyframes viewer-spin{to{transform:rotate(360deg)}}@media(max-width:640px){.resource-viewer-panel{height:96vh;border-radius:18px}header{padding:17px}footer{align-items:flex-start;flex-direction:column}.resource-viewer-pdf{min-height:480px}.resource-viewer-text{padding:18px;font-size:13px}}@media(prefers-reduced-motion:reduce){.resource-viewer-state i{animation:none}}
</style>
