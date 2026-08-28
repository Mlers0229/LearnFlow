import { reactive } from 'vue'

export const sessionExpiryState = reactive({
  visible: false,
  returnTo: '/',
})

export function showSessionExpired(returnTo = window.location.pathname + window.location.search) {
  sessionExpiryState.returnTo = returnTo || '/'
  sessionExpiryState.visible = true
}

export function closeSessionExpired() {
  sessionExpiryState.visible = false
}

export function createLoginUrl() {
  const redirect = sessionExpiryState.returnTo.startsWith('/login') ? '/' : sessionExpiryState.returnTo
  return `/login?redirect=${encodeURIComponent(redirect)}`
}
