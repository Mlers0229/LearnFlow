import { computed, reactive, ref } from 'vue'
import { createPrivacyExport, downloadPrivacyExport, getPrivacyRequest, requestAccountErasure } from '../../api/privacy'
import type { PrivacyProgress } from './types'

const sleep = (milliseconds: number) => new Promise((resolve) => setTimeout(resolve, milliseconds))
const failureCopy: Record<string, string> = {
  EXPORT_FAILED: '数据整理失败，请稍后重新生成。',
  ARTIFACT_EXPIRED: '下载文件已过期，请重新生成。',
  STORAGE_FAILED: '导出文件暂时无法保存，请稍后重试。',
}

export function usePrivacyCenter(options: { pollInterval?: number; maxPolls?: number } = {}) {
  const progress = reactive<PrivacyProgress>({ state: 'idle', percent: 0, title: '尚未生成导出', detail: '导出文件生成后保留 24 小时。' })
  const erasing = ref(false)
  const erasure = reactive({ password: '', confirmation: '', impactConfirmed: false })
  const username = ref('')
  const canErase = computed(() => Boolean(
    erasure.password
    && erasure.impactConfirmed
    && erasure.confirmation === `DELETE ${username.value}`,
  ))

  function setUsername(value: string) {
    username.value = value
    erasure.confirmation = ''
  }

  async function requestExport() {
    Object.assign(progress, { state: 'requesting', percent: 12, title: '正在创建导出任务', detail: '服务器正在确认你的数据范围。' })
    try {
      const request = await createPrivacyExport()
      progress.requestId = String(request.id)
      const maxPolls = options.maxPolls ?? 60
      const interval = options.pollInterval ?? 1000
      for (let attempt = 0; attempt < maxPolls; attempt += 1) {
        const current = await getPrivacyRequest(request.id)
        const status = String(current.status || '').toUpperCase()
        Object.assign(progress, {
          state: 'processing', percent: Math.min(90, 24 + attempt * 3),
          title: '正在整理个人数据', detail: '正在汇总账号、计划、练习、反馈和资源记录。',
        })
        if (status === 'SUCCEEDED' && current.downloadReady) {
          Object.assign(progress, { state: 'ready', percent: 100, title: '导出文件已就绪', detail: current.artifactExpiresAt ? `请在 ${new Date(current.artifactExpiresAt).toLocaleString('zh-CN')} 前下载。` : '文件将在 24 小时后自动删除。' })
          return current
        }
        if (status === 'FAILED') {
          const message = failureCopy[current.errorCode] || `导出失败（${current.errorCode || 'UNKNOWN'}），请稍后重试。`
          Object.assign(progress, { state: 'failed', percent: 100, title: '导出未完成', detail: message })
          throw new Error(message)
        }
        await sleep(interval)
      }
      throw new Error('导出仍在后台处理中，请稍后刷新状态。')
    } catch (error: unknown) {
      if (progress.state !== 'failed') Object.assign(progress, { state: 'failed', percent: 100, title: '导出未完成', detail: error instanceof Error ? error.message : '数据导出失败。' })
      throw error
    }
  }

  async function downloadExport() {
    if (!progress.requestId) throw new Error('请先生成数据导出。')
    const artifact = await downloadPrivacyExport(progress.requestId)
    const url = URL.createObjectURL(artifact.blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = artifact.filename
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    return artifact
  }

  async function eraseAccount() {
    if (!canErase.value) throw new Error('请完成密码、确认短语和影响确认。')
    erasing.value = true
    try {
      return await requestAccountErasure({ password: erasure.password, confirmation: erasure.confirmation })
    } finally {
      erasing.value = false
    }
  }

  return { progress, erasure, erasing, username, canErase, setUsername, requestExport, downloadExport, eraseAccount }
}
