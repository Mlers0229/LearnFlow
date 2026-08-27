<script setup lang="ts">
import { computed } from 'vue'
import type { ProfileFeedback, ProfileForm } from './types'

const props = defineProps<{ form: ProfileForm; dirty: boolean; passwordValid: boolean; feedback: ProfileFeedback }>()
const emit = defineEmits<{ oldPassword: [value: string]; newPassword: [value: string]; confirmPassword: [value: string]; save: []; reset: [] }>()
const passwordStrength = computed(() => {
  const value = props.form.newPassword
  if (!value) return { value: 0, label: '尚未输入', tone: 'empty' }
  let score = value.length >= 12 ? 1 : 0
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score += 1
  if (/\d/.test(value) && /[^\w\s]/.test(value)) score += 1
  return score >= 3 ? { value: 100, label: '强', tone: 'strong' } : score === 2 ? { value: 66, label: '中等', tone: 'medium' } : { value: 33, label: '较弱', tone: 'weak' }
})
</script>

<template>
  <form class="settings-panel" aria-labelledby="security-title" @submit.prevent="emit('save')">
    <input :value="form.username" type="text" name="username" autocomplete="username" class="visually-hidden" tabindex="-1" aria-hidden="true">
    <div class="panel-heading"><div><span>SECURITY</span><h2 id="security-title">登录安全</h2><p>更新密码会保留当前会话，其他设备将在令牌失效后需要重新登录。</p></div><span v-if="dirty" class="dirty-badge">密码待保存</span></div>
    <div class="security-note"><b>密码建议</b><p>至少 12 位，使用不同类型字符，并避免与其他网站重复。</p></div>
    <div class="password-grid">
      <label class="field wide"><span>当前密码</span><input :value="form.oldPassword" type="password" autocomplete="current-password" placeholder="验证当前身份" data-testid="old-password" @input="emit('oldPassword', ($event.target as HTMLInputElement).value)"></label>
      <label class="field"><span>新密码</span><input :value="form.newPassword" type="password" autocomplete="new-password" placeholder="至少 12 位" data-testid="new-password" @input="emit('newPassword', ($event.target as HTMLInputElement).value)"></label>
      <label class="field"><span>再次输入新密码</span><input :value="form.confirmPassword" type="password" autocomplete="new-password" placeholder="再次确认" data-testid="confirm-password" @input="emit('confirmPassword', ($event.target as HTMLInputElement).value)"></label>
    </div>
    <div class="strength"><div><span>密码强度</span><b :class="passwordStrength.tone">{{ passwordStrength.label }}</b></div><i><em :class="passwordStrength.tone" :style="{ width: `${passwordStrength.value}%` }" /></i></div>
    <p v-if="dirty && !passwordValid" class="validation">请填写当前密码，确保新密码至少 12 位且两次输入一致。</p>
    <div v-if="feedback.section === 'security' && feedback.state !== 'idle'" class="feedback" :class="feedback.state" aria-live="polite">{{ feedback.message }}</div>
    <div class="panel-actions"><button type="button" class="secondary" :disabled="!dirty || feedback.state === 'saving'" @click="emit('reset')">清空</button><button type="submit" class="primary" :disabled="!dirty || !passwordValid || feedback.state === 'saving'" data-testid="save-security">{{ feedback.section === 'security' && feedback.state === 'saving' ? '更新中…' : '更新密码' }}</button></div>
  </form>
</template>

<style scoped>
.settings-panel{display:grid;gap:22px}.panel-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:18px}.panel-heading>div>span{font-size:11px;font-weight:850;letter-spacing:.15em;color:#1a7967}.panel-heading h2{margin:7px 0 6px;font-size:30px;letter-spacing:-.04em;color:#193d36}.panel-heading p{margin:0;color:#6c817c;line-height:1.6}.dirty-badge{padding:6px 10px;border-radius:999px;background:#fff2d8;color:#855e12;font-size:11px;font-weight:800;white-space:nowrap}.security-note{padding:16px;border-radius:15px;background:#f5f8f7;border:1px solid #e0e9e6;color:#2f514a}.security-note p{margin:5px 0 0;color:#6b807a;font-size:13px}.password-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:16px}.field{display:grid;gap:8px}.field.wide{grid-column:1/-1}.field span{font-weight:750;color:#294b44}.field input{box-sizing:border-box;width:100%;border:1px solid #cbdcd7;border-radius:13px;padding:13px 14px;color:#183b34;font:inherit;outline:none}.field input:focus{border-color:#1b7967;box-shadow:0 0 0 3px rgba(27,121,103,.1)}.strength{display:grid;gap:8px}.strength>div{display:flex;justify-content:space-between;color:#6d817c;font-size:12px}.strength b.strong{color:#18705a}.strength b.medium{color:#a06c18}.strength b.weak{color:#a74738}.strength>i{display:block;height:6px;border-radius:999px;background:#e1e9e6;overflow:hidden}.strength em{display:block;height:100%;transition:width .2s}.strength em.strong{background:#1b846b}.strength em.medium{background:#d49a38}.strength em.weak{background:#c65c4b}.validation{margin:0;color:#a54738;font-size:13px}.feedback{padding:12px 14px;border-radius:12px;font-size:13px}.feedback.success{background:#ebf8f2;color:#17634e}.feedback.error{background:#fff0ed;color:#963f31}.feedback.saving{background:#eef5f3;color:#3b665d}.panel-actions{display:flex;justify-content:flex-end;gap:10px;padding-top:18px;border-top:1px solid #e5ece9}.panel-actions button{border-radius:11px;padding:11px 16px;font:inherit;font-weight:800;cursor:pointer}.panel-actions button:disabled{opacity:.45;cursor:not-allowed}.primary{border:0;background:#1b7765;color:#fff}.secondary{border:1px solid #cadbd6;background:#fff;color:#42635c}@media(max-width:620px){.panel-heading{flex-direction:column}.password-grid{grid-template-columns:1fr}.field.wide{grid-column:auto}.panel-actions button{flex:1}}
.visually-hidden{position:absolute!important;width:1px!important;height:1px!important;padding:0!important;margin:-1px!important;overflow:hidden!important;clip:rect(0,0,0,0)!important;white-space:nowrap!important;border:0!important}
</style>
