export type ResourceSourceType = 'URL' | 'TEXT' | 'DOCUMENT'

export type UploadStep = 1 | 2 | 3

export interface ResourceUploadForm {
  sourceType: ResourceSourceType
  url: string
  text: string
  file: File | null
  title: string
  domain: string
  level: string
  estimatedMinutes: number | null
  tags: string
  rightsConfirmed: boolean
}

export interface ResourceRecord {
  id?: number | string
  resourceId?: number | string
  title?: string
  url?: string
  sourceUrl?: string
  sourceType?: ResourceSourceType
  status?: string
  ingestionStatus?: string
  currentIngestionId?: number | string
  createdAt?: string
  updatedAt?: string
  rejectionReason?: string
  reviewReason?: string
  statusReason?: string
  ingestionErrorCode?: string
  [key: string]: unknown
}

export interface PreflightIssue {
  code: string
  message: string
  severity: 'error' | 'warning'
}

export type UploadPhase = 'idle' | 'validating' | 'uploading' | 'queued' | 'processing' | 'success' | 'failed'

export interface UploadProgressState {
  phase: UploadPhase
  percent: number
  title: string
  detail: string
  errorCode?: string
}
