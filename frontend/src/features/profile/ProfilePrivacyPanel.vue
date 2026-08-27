<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../store/auth'
import { usePrivacyCenter } from './usePrivacyCenter'

const { currentUser, isAdmin, logout } = useAuthStore()
const message = useMessage()
const router = useRouter()
const dangerOpen = ref(false)
const finalConfirmOpen = ref(false)
const username = computed(() => currentUser.value?.username || '')
const { progress, erasure, erasing, canErase, setUsername, requestExport, downloadExport, eraseAccount } = usePrivacyCenter()

onMounted(() => setUsername(username.value))

async function generateExport() {
  try {
    await requestExport()
    message.success('个人数据导出已经生成')
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : '数据导出失败')
  }
}

async function download() {
  try {
    await downloadExport()
    message.success('导出文件已开始下载')
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : '下载失败')
  }
}

async function confirmErasure() {
  try {
    await eraseAccount()
    finalConfirmOpen.value = false
    logout()
    message.success('账号已停用，数据删除正在后台执行')
    await router.replace('/login')
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : '账户注销请求失败')
  }
}
</script>

<template>
  <section class="settings-panel" aria-labelledby="privacy-title">
    <div class="panel-heading"><div><span>PRIVACY</span><h2 id="privacy-title">安全与隐私</h2><p>掌控个人数据副本、保留期限和账号生命周期。</p></div><span class="privacy-badge">数据最小化</span></div>

    <article class="privacy-card export-card">
      <div class="card-icon">↓</div>
      <div class="card-copy"><span>数据可携带</span><h3>导出我的 LearnFlow 数据</h3><p>生成版本化 JSON 文件，包含账号、计划、练习、反馈和资源记录。文件仅保留 24 小时。</p></div>
      <div class="card-action">
        <button v-if="progress.state !== 'ready'" type="button" :disabled="progress.state === 'requesting' || progress.state === 'processing'" data-testid="generate-export" @click="generateExport">{{ ['requesting','processing'].includes(progress.state) ? '生成中…' : progress.state === 'failed' ? '重新生成' : '生成数据副本' }}</button>
        <button v-else type="button" class="download" data-testid="download-export" @click="download">下载 JSON 文件</button>
      </div>
      <div v-if="progress.state !== 'idle'" class="export-progress" :class="progress.state" aria-live="polite">
        <div><strong>{{ progress.title }}</strong><b>{{ progress.percent }}%</b></div><i><em :style="{ width: `${progress.percent}%` }" /></i><p>{{ progress.detail }}</p>
      </div>
    </article>

    <article class="privacy-card session-card">
      <div class="card-icon">◎</div><div class="card-copy"><span>当前会话</span><h3>登录状态与本机设置</h3><p>退出登录会清除当前访问凭据；主题与字号仍保存在本机，可在学习偏好中调整。</p></div>
      <div class="card-action"><button type="button" class="quiet" @click="logout">退出当前账号</button></div>
    </article>

    <article class="privacy-card danger-card">
      <div class="card-icon">!</div><div class="card-copy"><span>DANGER ZONE</span><h3>停用并永久删除账号</h3><p>请求提交后会立即停止登录，随后异步删除学习计划、练习、反馈、个性化记录及上传的资源原件，无法撤销。</p></div>
      <div v-if="isAdmin" class="admin-lock"><strong>管理员自助注销已禁用</strong><span>为避免失去平台治理能力，请通过数据保护负责人执行受控流程。</span></div>
      <template v-else>
        <div class="card-action"><button type="button" class="danger" data-testid="open-erasure" @click="dangerOpen = !dangerOpen">{{ dangerOpen ? '收起' : '了解影响并继续' }}</button></div>
        <form v-if="dangerOpen" class="danger-form" @submit.prevent="finalConfirmOpen = true">
          <input :value="username" type="text" name="username" autocomplete="username" class="visually-hidden" tabindex="-1" aria-hidden="true">
          <div class="impact-grid"><div><b>立即发生</b><span>刷新凭据失效，无法再次登录。</span></div><div><b>后台执行</b><span>关联数据和上传原件被不可逆删除。</span></div><div><b>不会删除</b><span>依法必须保留的去标识化审计信息。</span></div></div>
          <label><span>当前密码</span><input v-model="erasure.password" type="password" autocomplete="current-password" data-testid="erasure-password"></label>
          <label><span>输入 DELETE {{ username }}</span><input v-model="erasure.confirmation" :placeholder="`DELETE ${username}`" data-testid="erasure-confirmation"></label>
          <label class="impact-check"><input v-model="erasure.impactConfirmed" type="checkbox" data-testid="erasure-impact"><span>我理解账号会立即停用，数据删除无法撤销。</span></label>
          <button type="submit" class="danger final" :disabled="!canErase" data-testid="prepare-erasure">继续最终确认</button>
        </form>
      </template>
    </article>

    <Teleport to="body">
      <div v-if="finalConfirmOpen" class="modal-backdrop" role="presentation" @click.self="finalConfirmOpen = false">
        <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="erasure-confirm-title">
          <span>不可撤销操作</span><h2 id="erasure-confirm-title">最后确认永久注销</h2><p>点击确认后，账号会立即停用，所有关联数据将进入不可逆删除流程。</p>
          <div><button type="button" class="cancel" :disabled="erasing" @click="finalConfirmOpen = false">取消</button><button type="button" class="danger" :disabled="erasing" data-testid="confirm-erasure" @click="confirmErasure">{{ erasing ? '正在停用…' : '确认停用并删除' }}</button></div>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.settings-panel{display:grid;gap:18px}.panel-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:18px;margin-bottom:4px}.panel-heading>div>span,.card-copy>span{font-size:11px;font-weight:850;letter-spacing:.15em;color:#1a7967}.panel-heading h2{margin:7px 0 6px;font-size:30px;letter-spacing:-.04em;color:#193d36}.panel-heading p{margin:0;color:#6c817c;line-height:1.6}.privacy-badge{padding:6px 10px;border-radius:999px;background:#eaf6f2;color:#1d6a58;font-size:11px;font-weight:800;white-space:nowrap}.privacy-card{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:15px;align-items:start;padding:19px;border:1px solid #dfe9e6;border-radius:18px;background:#fff}.card-icon{display:grid;place-items:center;width:40px;height:40px;border-radius:13px;background:#e9f5f1;color:#1b7461;font-weight:900;font-size:18px}.card-copy h3{margin:5px 0 5px;color:#284a43;font-size:17px}.card-copy p{margin:0;color:#6f827d;font-size:13px;line-height:1.6}.card-action button,.danger-form>button{border:1px solid #bfd4ce;border-radius:11px;background:#fff;color:#256657;padding:10px 13px;font:inherit;font-size:13px;font-weight:800;cursor:pointer}.card-action button:disabled,.danger-form>button:disabled{opacity:.45;cursor:not-allowed}.card-action .download{border-color:#1b7663;background:#1b7663;color:#fff}.card-action .quiet{color:#4f6963}.export-progress{grid-column:2/-1;padding:13px;border-radius:13px;background:#f1f7f5}.export-progress.failed{background:#fff0ed}.export-progress>div{display:flex;justify-content:space-between;color:#31564d;font-size:13px}.export-progress i{display:block;height:6px;margin:9px 0;border-radius:99px;background:#d7e5e1;overflow:hidden}.export-progress em{display:block;height:100%;background:#1a8069;transition:width .3s}.export-progress.failed em{background:#bd5545}.export-progress p{margin:0;color:#6a7d78;font-size:12px}.danger-card{border-color:#ecd0ca;background:#fffaf8}.danger-card .card-icon{background:#fae5e0;color:#a94335}.danger-card .card-copy>span{color:#a94335}.card-action .danger,.danger-form .danger{border-color:#b84d3d;color:#a33d30;background:#fff7f4}.admin-lock,.danger-form{grid-column:2/-1}.admin-lock{display:grid;gap:5px;padding:13px;border-radius:12px;background:#f6f1e7;color:#685a3b}.admin-lock span{font-size:12px;line-height:1.5}.danger-form{display:grid;gap:14px;padding:17px;border-top:1px solid #efd9d4}.impact-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.impact-grid>div{display:grid;gap:4px;padding:11px;border-radius:11px;background:#fff}.impact-grid b{font-size:12px;color:#8e3e32}.impact-grid span{font-size:11px;line-height:1.45;color:#776965}.danger-form label{display:grid;gap:7px;color:#593f3a;font-size:13px;font-weight:700}.danger-form input[type=password],.danger-form input[type=text],.danger-form label:not(.impact-check) input{box-sizing:border-box;width:100%;border:1px solid #dfc7c1;border-radius:11px;padding:11px 12px;font:inherit;outline:none}.danger-form input:focus{border-color:#aa4a3b;box-shadow:0 0 0 3px rgba(170,74,59,.09)}.impact-check{display:flex!important;align-items:flex-start;gap:9px;font-weight:600!important}.impact-check input{margin-top:2px;accent-color:#a84536}.danger-form>button.final{justify-self:start}.modal-backdrop{position:fixed;inset:0;z-index:3000;display:grid;place-items:center;padding:20px;background:rgba(14,34,29,.62);backdrop-filter:blur(5px)}.confirm-modal{max-width:440px;padding:27px;border-radius:22px;background:#fff;color:#25463f;box-shadow:0 30px 80px rgba(0,0,0,.25)}.confirm-modal>span{font-size:11px;font-weight:850;letter-spacing:.14em;color:#a43f31}.confirm-modal h2{margin:8px 0 10px;font-size:26px;letter-spacing:-.04em}.confirm-modal p{color:#6d7c78;line-height:1.7}.confirm-modal>div{display:flex;justify-content:flex-end;gap:9px;margin-top:22px}.confirm-modal button{border-radius:11px;padding:11px 14px;font:inherit;font-weight:800;cursor:pointer}.confirm-modal .cancel{border:1px solid #cfdcd8;background:#fff;color:#536b65}.confirm-modal .danger{border:0;background:#a94435;color:#fff}@media(max-width:760px){.privacy-card{grid-template-columns:auto 1fr}.card-action,.export-progress,.admin-lock,.danger-form{grid-column:1/-1}.impact-grid{grid-template-columns:1fr}}@media(max-width:620px){.panel-heading{flex-direction:column}.privacy-card{padding:16px}.card-action button{width:100%}}
.visually-hidden{position:absolute!important;width:1px!important;height:1px!important;padding:0!important;margin:-1px!important;overflow:hidden!important;clip:rect(0,0,0,0)!important;white-space:nowrap!important;border:0!important}
</style>
