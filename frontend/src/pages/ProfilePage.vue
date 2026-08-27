<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useMessage } from 'naive-ui'
import ProfileAccountPanel from '../features/profile/ProfileAccountPanel.vue'
import ProfilePreferencesPanel from '../features/profile/ProfilePreferencesPanel.vue'
import ProfilePrivacyPanel from '../features/profile/ProfilePrivacyPanel.vue'
import ProfileSecurityPanel from '../features/profile/ProfileSecurityPanel.vue'
import { useProfileSettings } from '../features/profile/useProfileSettings'
import { useAuthStore } from '../store/auth'
import type { ProfileSection } from '../features/profile/types'

const { currentUser } = useAuthStore()
const message = useMessage()
const activeSection = ref<ProfileSection>('account')
const {
  form, feedback, emailValid, passwordValid, accountDirty, preferencesDirty, securityDirty,
  hasUnsavedChanges, save, reset,
} = useProfileSettings()

const sections: Array<{ value: ProfileSection; label: string; detail: string }> = [
  { value: 'account', label: '账号资料', detail: '邮箱与身份信息' },
  { value: 'preferences', label: '学习偏好', detail: '水平、主题与字号' },
  { value: 'security', label: '登录安全', detail: '密码与会话提示' },
  { value: 'privacy', label: '安全与隐私', detail: '导出、退出与注销' },
]
const dirtySections = computed(() => ({ account: accountDirty.value, preferences: preferencesDirty.value, security: securityDirty.value, privacy: false }))
const roleLabel = computed(() => currentUser.value?.role === 'admin' ? '平台管理员' : '学习者')
const savedTime = computed(() => feedback.savedAt ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(feedback.savedAt) : '尚未修改')

async function saveSection(section: 'account' | 'preferences' | 'security') {
  try {
    await save(section)
    message.success('个人设置已保存')
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : '保存失败')
  }
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (!hasUnsavedChanges.value) return
  event.preventDefault()
  event.returnValue = ''
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
onBeforeRouteLeave(() => !hasUnsavedChanges.value || window.confirm('你还有未保存的个人设置，确定离开吗？'))
</script>

<template>
  <main class="profile-page">
    <section class="profile-hero">
      <div class="hero-identity">
        <div class="hero-avatar">{{ form.username.slice(0, 1).toUpperCase() || 'L' }}</div>
        <div><span>PERSONAL WORKSPACE</span><h1>{{ form.username || '个人设置' }}</h1><p>{{ roleLabel }} · 管理你的学习偏好、登录安全和个人数据。</p></div>
      </div>
      <div class="hero-status">
        <article><span>资料状态</span><strong>{{ hasUnsavedChanges ? '有待保存更改' : '已同步' }}</strong></article>
        <article><span>最近保存</span><strong>{{ savedTime }}</strong></article>
      </div>
    </section>

    <section class="settings-shell">
      <aside class="settings-nav">
        <div class="nav-heading"><span>SETTINGS</span><strong>设置中心</strong></div>
        <nav aria-label="个人设置分类">
          <button v-for="item in sections" :key="item.value" type="button" :class="{ active: activeSection === item.value }" @click="activeSection = item.value">
            <i>{{ sections.findIndex((entry) => entry.value === item.value) + 1 }}</i>
            <span><strong>{{ item.label }}</strong><small>{{ item.detail }}</small></span>
            <b v-if="dirtySections[item.value]" aria-label="有未保存更改" />
          </button>
        </nav>
        <div class="nav-note"><strong>隐私承诺</strong><p>设置只用于提供学习服务，不会用于公开展示或出售。</p></div>
      </aside>

      <div class="settings-content">
        <ProfileAccountPanel
          v-if="activeSection === 'account'" :form="form" :dirty="accountDirty" :email-valid="emailValid" :feedback="feedback"
          @email="form.email = $event" @save="saveSection('account')" @reset="reset('account')"
        />
        <ProfilePreferencesPanel
          v-else-if="activeSection === 'preferences'" :form="form" :dirty="preferencesDirty" :feedback="feedback"
          @level="form.level = $event" @save="saveSection('preferences')" @reset="reset('preferences')"
        />
        <ProfileSecurityPanel
          v-else-if="activeSection === 'security'" :form="form" :dirty="securityDirty" :password-valid="passwordValid" :feedback="feedback"
          @old-password="form.oldPassword = $event" @new-password="form.newPassword = $event" @confirm-password="form.confirmPassword = $event" @save="saveSection('security')" @reset="reset('security')"
        />
        <ProfilePrivacyPanel v-else />
      </div>
    </section>
  </main>
</template>

<style scoped>
.profile-page{min-height:100%;padding:clamp(18px,3vw,34px);background:#f4f0e8;color:#193b35}.profile-hero{display:flex;justify-content:space-between;align-items:flex-end;gap:30px;padding:clamp(28px,5vw,52px);border-radius:29px;background:#173f37;color:#f8f4e9;overflow:hidden;position:relative}.profile-hero:after{content:"";position:absolute;width:360px;height:360px;right:-160px;top:-210px;border-radius:50%;border:1px solid rgba(255,255,255,.13);box-shadow:0 0 0 65px rgba(255,255,255,.035),0 0 0 130px rgba(255,255,255,.02)}.hero-identity,.hero-status{position:relative;z-index:1}.hero-identity{display:flex;gap:20px;align-items:center}.hero-avatar{display:grid;place-items:center;flex:0 0 72px;width:72px;height:72px;border-radius:23px;background:#e3b55f;color:#173f37;font:500 34px Georgia,serif}.hero-identity span{font-size:11px;font-weight:850;letter-spacing:.17em;color:#a9d8ca}.hero-identity h1{margin:6px 0 4px;font:500 clamp(34px,5vw,54px) Georgia,serif;letter-spacing:-.045em}.hero-identity p{margin:0;color:#c6d7d2}.hero-status{display:grid;grid-template-columns:repeat(2,minmax(120px,1fr));border:1px solid rgba(255,255,255,.14);border-radius:17px;background:rgba(255,255,255,.055)}.hero-status article{display:grid;gap:6px;padding:16px 18px}.hero-status article+article{border-left:1px solid rgba(255,255,255,.12)}.hero-status span{font-size:11px;color:#a9c3bc}.hero-status strong{font-size:13px;color:#f1cf8c}.settings-shell{display:grid;grid-template-columns:260px minmax(0,1fr);gap:22px;margin-top:23px}.settings-nav,.settings-content{border-radius:23px;background:#fff;box-shadow:0 12px 34px rgba(51,65,59,.06)}.settings-nav{align-self:start;position:sticky;top:18px;padding:18px}.nav-heading{display:grid;gap:4px;padding:8px 9px 16px}.nav-heading span{font-size:10px;font-weight:850;letter-spacing:.16em;color:#1b7664}.nav-heading strong{font-size:20px;color:#23473f}.settings-nav nav{display:grid;gap:5px}.settings-nav nav button{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:11px;border:0;border-radius:13px;background:transparent;padding:11px;text-align:left;color:#55706a;cursor:pointer}.settings-nav nav button.active{background:#edf7f3;color:#1c5e50}.settings-nav nav i{display:grid;place-items:center;width:27px;height:27px;border-radius:9px;background:#eef2f0;font-style:normal;font-size:11px;font-weight:800}.settings-nav nav .active i{background:#1c7663;color:#fff}.settings-nav nav span{display:grid;gap:3px}.settings-nav nav strong{font-size:13px}.settings-nav nav small{font-size:10px;color:#81918d}.settings-nav nav b{width:7px;height:7px;border-radius:50%;background:#d19632}.nav-note{margin-top:16px;padding:14px;border-radius:13px;background:#faf5e9;color:#635c49}.nav-note p{margin:5px 0 0;font-size:11px;line-height:1.55;color:#827966}.settings-content{padding:clamp(22px,4vw,38px);min-width:0}@media(max-width:900px){.profile-hero{align-items:flex-start;flex-direction:column}.settings-shell{grid-template-columns:1fr}.settings-nav{position:static}.settings-nav nav{grid-template-columns:repeat(4,1fr)}.settings-nav nav button{display:flex;justify-content:center}.settings-nav nav button span,.settings-nav nav button b{display:none}.nav-heading,.nav-note{display:none}}@media(max-width:600px){.profile-page{padding:12px}.profile-hero{padding:25px 20px;border-radius:22px}.hero-identity{align-items:flex-start}.hero-avatar{width:54px;height:54px;flex-basis:54px;border-radius:17px;font-size:26px}.hero-identity p{font-size:12px;line-height:1.5}.hero-status{width:100%}.hero-status article{padding:13px}.settings-shell{gap:12px;margin-top:12px}.settings-nav,.settings-content{border-radius:19px}.settings-nav{padding:8px}.settings-nav nav button{padding:8px}.settings-content{padding:18px}}
</style>
