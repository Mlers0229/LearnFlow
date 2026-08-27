<script setup lang="ts">
import type { ProfileFeedback, ProfileForm } from './types'

defineProps<{ form: ProfileForm; dirty: boolean; emailValid: boolean; feedback: ProfileFeedback }>()
const emit = defineEmits<{ email: [value: string]; save: []; reset: [] }>()
</script>

<template>
  <section class="settings-panel" aria-labelledby="account-title">
    <div class="panel-heading"><div><span>ACCOUNT</span><h2 id="account-title">账号资料</h2><p>用于识别你的学习空间和接收必要的账户通知。</p></div><span v-if="dirty" class="dirty-badge">有未保存更改</span></div>
    <div class="identity-card">
      <div class="identity-avatar">{{ form.username.slice(0, 1).toUpperCase() || 'L' }}</div>
      <div><strong>{{ form.username || 'LearnFlow 用户' }}</strong><span>用户名不可自行修改</span></div>
    </div>
    <label class="field">
      <span>邮箱地址</span>
      <input :value="form.email" type="email" autocomplete="email" placeholder="name@example.com" data-testid="profile-email" @input="emit('email', ($event.target as HTMLInputElement).value)">
      <small :class="{ invalid: !emailValid }">{{ emailValid ? '用于账户通知和找回密码，不会公开显示。' : '请输入有效的邮箱地址。' }}</small>
    </label>
    <div v-if="feedback.section === 'account' && feedback.state !== 'idle'" class="feedback" :class="feedback.state" aria-live="polite">{{ feedback.message }}</div>
    <div class="panel-actions">
      <button type="button" class="secondary" :disabled="!dirty || feedback.state === 'saving'" @click="emit('reset')">撤销更改</button>
      <button type="button" class="primary" :disabled="!dirty || !emailValid || feedback.state === 'saving'" data-testid="save-account" @click="emit('save')">{{ feedback.section === 'account' && feedback.state === 'saving' ? '保存中…' : '保存账号资料' }}</button>
    </div>
  </section>
</template>

<style scoped>
.settings-panel{display:grid;gap:24px}.panel-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:18px}.panel-heading>div>span{font-size:11px;font-weight:850;letter-spacing:.15em;color:#1a7967}.panel-heading h2{margin:7px 0 6px;font-size:30px;letter-spacing:-.04em;color:#193d36}.panel-heading p{margin:0;color:#6c817c;line-height:1.6}.dirty-badge{padding:6px 10px;border-radius:999px;background:#fff2d8;color:#855e12;font-size:11px;font-weight:800;white-space:nowrap}.identity-card{display:flex;align-items:center;gap:14px;padding:17px;border-radius:17px;background:#f3f8f6;border:1px solid #dfebe7}.identity-avatar{display:grid;place-items:center;width:48px;height:48px;border-radius:15px;background:#1b705f;color:#fff;font-family:Georgia,serif;font-size:22px}.identity-card>div:last-child{display:grid;gap:4px}.identity-card strong{color:#23483f}.identity-card span{color:#718680;font-size:12px}.field{display:grid;gap:9px}.field>span{font-weight:750;color:#294b44}.field input{box-sizing:border-box;width:100%;border:1px solid #cbdcd7;border-radius:13px;padding:13px 14px;background:#fff;color:#183b34;font:inherit;outline:none}.field input:focus{border-color:#1b7967;box-shadow:0 0 0 3px rgba(27,121,103,.1)}.field small{color:#71837f}.field small.invalid{color:#a84737}.feedback{padding:12px 14px;border-radius:12px;font-size:13px}.feedback.success{background:#ebf8f2;color:#17634e}.feedback.error{background:#fff0ed;color:#963f31}.feedback.saving{background:#eef5f3;color:#3b665d}.panel-actions{display:flex;justify-content:flex-end;gap:10px;padding-top:18px;border-top:1px solid #e5ece9}.panel-actions button{border-radius:11px;padding:11px 16px;font:inherit;font-weight:800;cursor:pointer}.panel-actions button:disabled{opacity:.45;cursor:not-allowed}.primary{border:0;background:#1b7765;color:#fff}.secondary{border:1px solid #cadbd6;background:#fff;color:#42635c}@media(max-width:620px){.panel-heading{flex-direction:column}.panel-actions{justify-content:stretch}.panel-actions button{flex:1}}
</style>
