import { describe, expect, it } from 'vitest'
import { ingestionErrorMessage, MAX_FILE_BYTES, normalizeUrl, reviewReason, validateResource } from './resourceUploadUtils'
import type { ResourceUploadForm } from './types'

function form(overrides: Partial<ResourceUploadForm> = {}): ResourceUploadForm {
  return {
    sourceType: 'URL',
    url: 'https://learn.example.com/guide',
    text: '',
    file: null,
    title: '前端学习指南',
    domain: '编程开发',
    level: '入门',
    estimatedMinutes: 30,
    tags: 'Vue, 前端',
    rightsConfirmed: true,
    ...overrides,
  }
}

describe('resource upload preflight', () => {
  it('accepts a valid public URL', () => {
    expect(validateResource(form())).toEqual([])
  })

  it.each([
    ['http://127.0.0.1/admin', 'URL_PRIVATE'],
    ['http://192.168.1.8/file', 'URL_PRIVATE'],
    ['https://user:secret@example.com/file', 'URL_CREDENTIALS'],
    ['https://example.com:8443/file', 'URL_PORT'],
    ['ftp://example.com/file', 'URL_PROTOCOL'],
  ])('blocks unsafe URL %s', (url, code) => {
    expect(validateResource(form({ url })).some((issue) => issue.code === code)).toBe(true)
  })

  it('normalizes links before duplicate detection', () => {
    expect(normalizeUrl('HTTPS://EXAMPLE.COM/docs/#intro')).toBe('https://example.com/docs')
    const issues = validateResource(form({ url: 'https://example.com/docs#new' }), [{ sourceUrl: 'https://example.com/docs/' }])
    expect(issues.some((issue) => issue.code === 'URL_DUPLICATE')).toBe(true)
  })

  it('checks document format and size before upload', () => {
    const invalidType = new File(['unsafe'], 'lesson.exe', { type: 'application/octet-stream' })
    const tooLarge = new File(['content'], 'lesson.pdf', { type: 'application/pdf' })
    Object.defineProperty(tooLarge, 'size', { value: MAX_FILE_BYTES + 1 })

    expect(validateResource(form({ sourceType: 'DOCUMENT', file: invalidType })).some((issue) => issue.code === 'FILE_TYPE')).toBe(true)
    expect(validateResource(form({ sourceType: 'DOCUMENT', file: tooLarge })).some((issue) => issue.code === 'FILE_SIZE')).toBe(true)
  })

  it('distinguishes blocking errors from same-title warnings', () => {
    const issues = validateResource(form(), [{ title: '前端学习指南' }])
    expect(issues).toContainEqual(expect.objectContaining({ code: 'TITLE_DUPLICATE', severity: 'warning' }))
    expect(issues.some((issue) => issue.severity === 'error')).toBe(false)
  })

  it('turns ingestion and review failures into useful copy', () => {
    expect(ingestionErrorMessage('SOURCE_TOO_LARGE')).toContain('10 MB')
    expect(reviewReason({ rejectionReason: '缺少来源说明' })).toBe('缺少来源说明')
  })
})
