import { computed, reactive } from 'vue'
import { getResourceSource } from '../../../api/resource'
import { getUserFacingError } from '../../../shared/api/errors'

export type ViewableResource = {
  id?: number | string | null
  resourceId?: number | string | null
  title?: string | null
  url?: string | null
  sourceType?: string | null
  ingestionStatus?: string | null
}

type ViewerMode = 'IDLE' | 'LOADING' | 'TEXT' | 'PDF' | 'ERROR'

const state = reactive({
  visible: false,
  mode: 'IDLE' as ViewerMode,
  title: '',
  filename: '',
  contentType: '',
  text: '',
  objectUrl: '',
  error: '',
  blob: null as Blob | null,
})

function resourceId(resource: ViewableResource) {
  return resource.resourceId ?? resource.id
}

function safeExternalUrl(value?: string | null) {
  const url = String(value || '').trim()
  return /^https?:\/\//i.test(url) ? url : ''
}

function releaseObjectUrl() {
  if (state.objectUrl) URL.revokeObjectURL(state.objectUrl)
  state.objectUrl = ''
}

function clearContent() {
  releaseObjectUrl()
  state.text = ''
  state.error = ''
  state.blob = null
  state.filename = ''
  state.contentType = ''
}

function closeViewer() {
  state.visible = false
  state.mode = 'IDLE'
  clearContent()
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename || 'learnflow-resource'
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  globalThis.setTimeout(() => URL.revokeObjectURL(url), 1_000)
}

async function openResource(resource: ViewableResource) {
  const externalUrl = safeExternalUrl(resource.url)
  if (externalUrl) {
    const opened = window.open(externalUrl, '_blank', 'noopener,noreferrer')
    if (opened) opened.opener = null
    return
  }

  clearContent()
  state.visible = true
  state.mode = 'LOADING'
  state.title = String(resource.title || '学习资源')
  const id = resourceId(resource)
  if (id == null) {
    state.mode = 'ERROR'
    state.error = '资源标识缺失，暂时无法查看。'
    return
  }

  try {
    const source = await getResourceSource(id)
    state.filename = source.filename
    state.contentType = source.contentType
    state.blob = source.blob
    if (source.viewMode === 'DOWNLOAD') {
      triggerDownload(source.blob, source.filename)
      closeViewer()
      return
    }
    if (source.viewMode === 'INLINE_PDF' || source.contentType === 'application/pdf') {
      state.objectUrl = URL.createObjectURL(source.blob)
      state.mode = 'PDF'
      return
    }
    state.text = await source.blob.text()
    state.mode = 'TEXT'
  } catch (error) {
    state.mode = 'ERROR'
    state.error = getUserFacingError(error, '资源原件暂时无法查看，请稍后重试。')
  }
}

function downloadCurrent() {
  if (state.blob) triggerDownload(state.blob, state.filename)
}

function canViewResource(resource: ViewableResource) {
  if (safeExternalUrl(resource.url)) return true
  const type = String(resource.sourceType || '').toUpperCase()
  if (type === 'URL') return false
  const status = String(resource.ingestionStatus || '').toUpperCase()
  return resourceId(resource) != null && (!status || status === 'SUCCEEDED')
}

function resourceActionLabel(resource: ViewableResource) {
  if (safeExternalUrl(resource.url)) return '打开链接'
  return String(resource.sourceType || '').toUpperCase() === 'TEXT' ? '查看正文' : '查看文件'
}

export function useResourceViewer() {
  return {
    state,
    loading: computed(() => state.mode === 'LOADING'),
    canViewResource,
    resourceActionLabel,
    openResource,
    closeViewer,
    downloadCurrent,
  }
}
