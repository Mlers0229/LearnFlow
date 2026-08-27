import { computed, reactive, ref } from 'vue'
import {
  getResourceIngestion,
  listMyResources,
  reingestResourceUrl,
  submitResourceDocument,
  submitResourceText,
  submitResourceUrl,
} from '../../../api/resource'
import { ingestionErrorMessage, reviewReason, validateResource } from './resourceUploadUtils'
import type { ResourceRecord, ResourceSourceType, ResourceUploadForm, UploadProgressState, UploadStep } from './types'

const emptyProgress = (): UploadProgressState => ({ phase: 'idle', percent: 0, title: '等待提交', detail: '完成三步检查后开始处理。' })
const makeKey = () => `resource-${Date.now()}-${Math.random().toString(36).slice(2)}`
const sleep = (milliseconds: number) => new Promise((resolve) => setTimeout(resolve, milliseconds))

export function useResourceUpload(options: { pollInterval?: number; maxPolls?: number } = {}) {
  const step = ref<UploadStep>(1)
  const records = ref<ResourceRecord[]>([])
  const recordsLoading = ref(false)
  const submitting = ref(false)
  const progress = ref<UploadProgressState>(emptyProgress())
  const lastResourceId = ref<string | number | null>(null)
  const lastIngestionId = ref<string | number | null>(null)
  const form = reactive<ResourceUploadForm>({
    sourceType: 'URL', url: '', text: '', file: null, title: '', domain: '', level: '',
    estimatedMinutes: null, tags: '', rightsConfirmed: false,
  })

  const issues = computed(() => validateResource(form, records.value))
  const errors = computed(() => issues.value.filter((issue) => issue.severity === 'error'))
  const warnings = computed(() => issues.value.filter((issue) => issue.severity === 'warning'))
  const canSubmit = computed(() => errors.value.length === 0 && !submitting.value)
  const statusCounts = computed(() => records.value.reduce<{ total: number; active: number; pending: number; inactive: number }>((counts, record) => {
    const status = String(record.status || 'PENDING').toUpperCase()
    counts.total += 1
    if (status === 'ACTIVE') counts.active += 1
    else if (status === 'INACTIVE') counts.inactive += 1
    else counts.pending += 1
    return counts
  }, { total: 0, active: 0, pending: 0, inactive: 0 }))

  async function loadRecords() {
    recordsLoading.value = true
    try {
      const response = await listMyResources()
      const payload = response?.data ?? response
      records.value = Array.isArray(payload) ? payload : (payload?.content || payload?.items || [])
    } finally {
      recordsLoading.value = false
    }
  }

  function setSourceType(sourceType: ResourceSourceType) {
    form.sourceType = sourceType
    progress.value = emptyProgress()
  }

  async function pollIngestion(ingestionId: string | number) {
    const maxPolls = options.maxPolls ?? 20
    const interval = options.pollInterval ?? 900
    for (let attempt = 0; attempt < maxPolls; attempt += 1) {
      const response = await getResourceIngestion(ingestionId)
      const result = response?.data ?? response
      const status = String(result?.status || '').toUpperCase()
      if (status === 'SUCCEEDED' || status === 'SUCCESS' || status === 'COMPLETED') {
        progress.value = { phase: 'success', percent: 100, title: '处理完成，等待审核', detail: '资源已进入审核队列，可在下方“我的提交”继续跟踪。' }
        return result
      }
      if (status === 'FAILED') {
        const errorCode = result?.errorCode
        progress.value = { phase: 'failed', percent: 100, title: '处理失败', detail: ingestionErrorMessage(errorCode), errorCode }
        throw new Error(ingestionErrorMessage(errorCode))
      }
      progress.value = {
        phase: status === 'QUEUED' ? 'queued' : 'processing',
        percent: Math.min(94, 64 + attempt * 4),
        title: status === 'QUEUED' ? '已进入处理队列' : '正在解析资源',
        detail: status === 'QUEUED' ? '服务器已接收，正在等待解析。' : '正在提取正文、识别结构并生成索引。',
      }
      await sleep(interval)
    }
    progress.value = { phase: 'processing', percent: 95, title: '仍在后台处理', detail: '无需停留在此页面，可稍后刷新“我的提交”查看结果。' }
    return null
  }

  function payload() {
    return {
      title: form.title.trim(),
      domain: form.domain,
      level: form.level || undefined,
      durationMinutes: form.estimatedMinutes || undefined,
      rightsConfirmed: form.rightsConfirmed,
      tags: form.tags.trim() || undefined,
      url: form.url.trim(),
      text: form.text,
    }
  }

  async function submit() {
    progress.value = { phase: 'validating', percent: 10, title: '正在校验', detail: '检查链接安全、重复资源、格式与大小限制。' }
    if (errors.value.length) throw new Error(errors.value[0].message)
    submitting.value = true
    try {
      progress.value = { phase: 'uploading', percent: 35, title: '正在提交', detail: form.sourceType === 'DOCUMENT' ? '正在上传文档，请保持页面打开。' : '正在安全传输资源内容。' }
      const key = makeKey()
      let response
      if (form.sourceType === 'URL') response = await submitResourceUrl(payload(), key)
      else if (form.sourceType === 'TEXT') response = await submitResourceText(payload(), key)
      else response = await submitResourceDocument(payload(), form.file, key)
      const result = response?.data ?? response
      lastResourceId.value = result?.resourceId ?? null
      lastIngestionId.value = result?.ingestionId ?? null
      progress.value = { phase: 'queued', percent: 60, title: '提交成功，等待处理', detail: '资源已经进入后台解析队列。' }
      if (lastIngestionId.value != null) await pollIngestion(lastIngestionId.value)
      else progress.value = { phase: 'success', percent: 100, title: '提交成功，等待审核', detail: '可在下方“我的提交”继续跟踪。' }
      await loadRecords()
      return result
    } catch (error: unknown) {
      if (progress.value.phase !== 'failed') {
        const detail = error instanceof Error ? error.message : '提交失败，请稍后重试。'
        progress.value = { phase: 'failed', percent: 100, title: '提交未完成', detail }
      }
      throw error
    } finally {
      submitting.value = false
    }
  }

  async function retry(record: ResourceRecord) {
    const resourceId = record.resourceId ?? record.id
    const url = String(record.url || record.sourceUrl || '')
    if (record.sourceType === 'URL' && resourceId != null && url) {
      submitting.value = true
      progress.value = { phase: 'uploading', percent: 35, title: '正在重新提交', detail: '保留原资源并创建新的处理任务。' }
      try {
        const response = await reingestResourceUrl(resourceId, url, makeKey())
        const result = response?.data ?? response
        lastResourceId.value = result?.resourceId ?? resourceId
        lastIngestionId.value = result?.ingestionId ?? null
        if (lastIngestionId.value != null) await pollIngestion(lastIngestionId.value)
        await loadRecords()
      } finally {
        submitting.value = false
      }
      return
    }
    form.sourceType = (record.sourceType || 'TEXT') as ResourceSourceType
    form.title = String(record.title || '')
    form.url = url
    form.text = ''
    form.file = null
    step.value = 1
    progress.value = { phase: 'idle', percent: 0, title: '已回填资源信息', detail: '请重新提供正文或文档，再完成提交。' }
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function reset() {
    Object.assign(form, { sourceType: 'URL', url: '', text: '', file: null, title: '', domain: '', level: '', estimatedMinutes: null, tags: '', rightsConfirmed: false })
    step.value = 1
    progress.value = emptyProgress()
    lastResourceId.value = null
    lastIngestionId.value = null
  }

  return { form, step, records, recordsLoading, submitting, progress, issues, errors, warnings, canSubmit, statusCounts, lastResourceId, lastIngestionId, loadRecords, setSourceType, submit, retry, reset, reviewReason }
}
