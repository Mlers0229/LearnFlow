import type { PreflightIssue, ResourceRecord, ResourceUploadForm } from './types'

export const MAX_FILE_BYTES = 10 * 1024 * 1024
export const MAX_TEXT_LENGTH = 2_000_000
export const MAX_URL_LENGTH = 2048
export const ACCEPTED_EXTENSIONS = ['pdf', 'doc', 'docx', 'txt', 'md', 'rtf']

const ERROR_MESSAGES: Record<string, string> = {
  UNSUPPORTED_CONTENT_TYPE: '暂不支持这种文件格式，请改用 PDF、Word、TXT、Markdown 或 RTF。',
  SOURCE_TOO_LARGE: '资源超过 10 MB，请压缩或拆分后重新提交。',
  INVALID_URL: '链接格式无效，请检查后重试。',
  URL_NOT_PUBLIC: '该链接指向内网或本机地址，出于安全原因无法抓取。',
  DOWNLOAD_FAILED: '链接内容下载失败，请确认页面可公开访问。',
  PARSE_FAILED: '内容解析失败，请更换格式或直接粘贴文本。',
  INGESTION_FAILED: '资源处理失败，请稍后重试。',
}

export function normalizeUrl(value: string) {
  try {
    const url = new URL(value.trim())
    url.hash = ''
    if (url.pathname !== '/') url.pathname = url.pathname.replace(/\/$/, '')
    return url.toString().toLowerCase()
  } catch {
    return value.trim().toLowerCase()
  }
}

function isPrivateIpv4(hostname: string) {
  const parts = hostname.split('.').map(Number)
  if (parts.length !== 4 || parts.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) return false
  return parts[0] === 10
    || parts[0] === 127
    || (parts[0] === 169 && parts[1] === 254)
    || (parts[0] === 192 && parts[1] === 168)
    || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31)
}

export function validateResource(form: ResourceUploadForm, records: ResourceRecord[] = []): PreflightIssue[] {
  const issues: PreflightIssue[] = []
  const title = form.title.trim()

  if (!title) issues.push({ code: 'TITLE_REQUIRED', message: '请填写资源标题。', severity: 'error' })
  if (title.length > 300) issues.push({ code: 'TITLE_TOO_LONG', message: '标题不能超过 300 个字符。', severity: 'error' })
  if (!form.domain) issues.push({ code: 'DOMAIN_REQUIRED', message: '请选择知识领域。', severity: 'error' })
  if (form.tags.length > 1000) issues.push({ code: 'TAGS_TOO_LONG', message: '标签内容不能超过 1000 个字符。', severity: 'error' })
  if (!form.rightsConfirmed) issues.push({ code: 'RIGHTS_REQUIRED', message: '请确认你有权提交并处理这份内容。', severity: 'error' })

  if (form.sourceType === 'URL') {
    const rawUrl = form.url.trim()
    if (!rawUrl) {
      issues.push({ code: 'URL_REQUIRED', message: '请输入公开可访问的资源链接。', severity: 'error' })
    } else if (rawUrl.length > MAX_URL_LENGTH) {
      issues.push({ code: 'URL_TOO_LONG', message: '链接不能超过 2048 个字符。', severity: 'error' })
    } else {
      try {
        const parsed = new URL(rawUrl)
        const hostname = parsed.hostname.toLowerCase()
        if (!['http:', 'https:'].includes(parsed.protocol)) {
          issues.push({ code: 'URL_PROTOCOL', message: '仅支持 HTTP 或 HTTPS 链接。', severity: 'error' })
        }
        if (parsed.username || parsed.password) {
          issues.push({ code: 'URL_CREDENTIALS', message: '链接不能包含用户名或密码。', severity: 'error' })
        }
        if (parsed.port && !['80', '443'].includes(parsed.port)) {
          issues.push({ code: 'URL_PORT', message: '链接使用了非标准端口，暂不允许提交。', severity: 'error' })
        }
        if (hostname === 'localhost' || hostname.endsWith('.local') || hostname === '::1' || isPrivateIpv4(hostname)) {
          issues.push({ code: 'URL_PRIVATE', message: '链接指向本机或内网地址，无法安全抓取。', severity: 'error' })
        }
        const normalized = normalizeUrl(rawUrl)
        const duplicate = records.some((record) => {
          const existing = String(record.url || record.sourceUrl || '').trim()
          return existing && normalizeUrl(existing) === normalized
        })
        if (duplicate) issues.push({ code: 'URL_DUPLICATE', message: '这个链接已经提交过，可在“我的提交”中查看或重试。', severity: 'error' })
      } catch {
        issues.push({ code: 'URL_INVALID', message: '链接格式不正确，请输入完整网址。', severity: 'error' })
      }
    }
  }

  if (form.sourceType === 'TEXT') {
    if (!form.text.trim()) issues.push({ code: 'TEXT_REQUIRED', message: '请粘贴需要处理的正文。', severity: 'error' })
    if (form.text.length > MAX_TEXT_LENGTH) issues.push({ code: 'TEXT_TOO_LONG', message: '正文不能超过 200 万个字符。', severity: 'error' })
  }

  if (form.sourceType === 'DOCUMENT') {
    if (!form.file) {
      issues.push({ code: 'FILE_REQUIRED', message: '请选择需要上传的文档。', severity: 'error' })
    } else {
      const extension = form.file.name.split('.').pop()?.toLowerCase() || ''
      if (!ACCEPTED_EXTENSIONS.includes(extension)) {
        issues.push({ code: 'FILE_TYPE', message: '文件格式不支持，请选择 PDF、Word、TXT、Markdown 或 RTF。', severity: 'error' })
      }
      if (form.file.size > MAX_FILE_BYTES) {
        issues.push({ code: 'FILE_SIZE', message: '文件不能超过 10 MB。', severity: 'error' })
      }
    }
  }

  const sameTitle = title && records.some((record) => String(record.title || '').trim().toLowerCase() === title.toLowerCase())
  if (sameTitle) issues.push({ code: 'TITLE_DUPLICATE', message: '已有同名资源，请确认这不是重复提交。', severity: 'warning' })
  return issues
}

export function ingestionErrorMessage(code?: string) {
  if (!code) return '资源处理失败，请稍后重试。'
  return ERROR_MESSAGES[code] || `资源处理失败（${code}），请稍后重试。`
}

export function reviewReason(record: ResourceRecord) {
  return String(record.rejectionReason || record.reviewReason || record.statusReason || record.ingestionErrorCode || '').trim()
}

export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
