export type ProfileSection = 'account' | 'preferences' | 'security' | 'privacy'
export type SaveState = 'idle' | 'saving' | 'success' | 'error'

export interface ProfileForm {
  username: string
  email: string
  level: string
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export interface ProfileFeedback {
  state: SaveState
  message: string
  section: ProfileSection | null
  savedAt: Date | null
}

export interface PrivacyProgress {
  state: 'idle' | 'requesting' | 'processing' | 'ready' | 'failed'
  percent: number
  title: string
  detail: string
  requestId?: string
}
