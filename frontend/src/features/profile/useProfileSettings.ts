import { computed, reactive } from 'vue'
import { updateProfile } from '../../api/auth'
import { useAuthStore } from '../../store/auth'
import type { ProfileFeedback, ProfileForm, ProfileSection } from './types'

function normalize(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

export function useProfileSettings() {
  const { currentUser, setUser } = useAuthStore()
  const initial = reactive({
    email: normalize(currentUser.value?.email),
    level: normalize(currentUser.value?.level),
  })
  const form = reactive<ProfileForm>({
    username: normalize(currentUser.value?.username),
    email: initial.email,
    level: initial.level,
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  })
  const feedback = reactive<ProfileFeedback>({ state: 'idle', message: '', section: null, savedAt: null })

  const emailValid = computed(() => !form.email || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
  const passwordValid = computed(() => {
    if (!form.oldPassword && !form.newPassword && !form.confirmPassword) return true
    return Boolean(form.oldPassword && form.newPassword.length >= 12 && form.newPassword === form.confirmPassword)
  })
  const accountDirty = computed(() => normalize(form.email) !== initial.email)
  const preferencesDirty = computed(() => form.level !== initial.level)
  const securityDirty = computed(() => Boolean(form.oldPassword || form.newPassword || form.confirmPassword))
  const hasUnsavedChanges = computed(() => accountDirty.value || preferencesDirty.value || securityDirty.value)

  function validationMessage(section: ProfileSection) {
    if (section === 'account' && !emailValid.value) return '请输入有效的邮箱地址。'
    if (section === 'security') {
      if (!form.oldPassword) return '修改密码前请填写当前密码。'
      if (form.newPassword.length < 12) return '新密码至少需要 12 位。'
      if (form.newPassword !== form.confirmPassword) return '两次输入的新密码不一致。'
    }
    return ''
  }

  async function save(section: Exclude<ProfileSection, 'privacy'>) {
    const validation = validationMessage(section)
    if (validation) {
      Object.assign(feedback, { state: 'error', message: validation, section })
      throw new Error(validation)
    }

    const payload: Record<string, string | undefined> = {}
    if (section === 'account') payload.email = normalize(form.email)
    if (section === 'preferences') payload.level = form.level || undefined
    if (section === 'security') {
      payload.oldPassword = form.oldPassword
      payload.newPassword = form.newPassword
    }
    Object.assign(feedback, { state: 'saving', message: '正在安全保存…', section })
    try {
      const response = await updateProfile(payload)
      if (section === 'account') initial.email = normalize(response?.email ?? form.email)
      if (section === 'preferences') initial.level = normalize(response?.level ?? form.level)
      if (section === 'security') {
        form.oldPassword = ''
        form.newPassword = ''
        form.confirmPassword = ''
      }
      setUser({ ...currentUser.value, email: response?.email ?? form.email, level: response?.level ?? form.level })
      Object.assign(feedback, { state: 'success', message: '更改已保存', section, savedAt: new Date() })
      return response
    } catch (error: unknown) {
      const message = errorMessage(error, '保存失败，请稍后重试。')
      Object.assign(feedback, { state: 'error', message, section })
      throw error
    }
  }

  function reset(section: Exclude<ProfileSection, 'privacy'>) {
    if (section === 'account') form.email = initial.email
    if (section === 'preferences') form.level = initial.level
    if (section === 'security') {
      form.oldPassword = ''
      form.newPassword = ''
      form.confirmPassword = ''
    }
    Object.assign(feedback, { state: 'idle', message: '', section: null })
  }

  return {
    form, feedback, emailValid, passwordValid, accountDirty, preferencesDirty, securityDirty,
    hasUnsavedChanges, validationMessage, save, reset,
  }
}
