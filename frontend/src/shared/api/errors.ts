export type ApiErrorKind =
  | 'authentication'
  | 'authorization'
  | 'validation'
  | 'conflict'
  | 'rate-limit'
  | 'server'
  | 'network'
  | 'unknown';

type ApiProblem = {
  code?: string;
  message?: string;
  detail?: string;
  title?: string;
};

function classifyStatus(status: number): ApiErrorKind {
  if (status === 401) return 'authentication';
  if (status === 403) return 'authorization';
  if (status === 409) return 'conflict';
  if (status === 422 || status === 400) return 'validation';
  if (status === 429) return 'rate-limit';
  if (status >= 500) return 'server';
  return 'unknown';
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly kind: ApiErrorKind;
  readonly detail?: string;

  constructor(message: string, options: { status?: number; code?: string; kind?: ApiErrorKind; detail?: string } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = options.status ?? 0;
    this.code = options.code;
    this.kind = options.kind ?? (this.status ? classifyStatus(this.status) : 'unknown');
    this.detail = options.detail;
  }
}

export async function apiErrorFromResponse(response: Response, fallback: string) {
  let problem: ApiProblem = {};
  try {
    const body = await response.clone().json();
    if (body && typeof body === 'object') problem = body as ApiProblem;
  } catch {
    // Non-JSON responses are represented by the stable fallback below.
  }

  return new ApiError(problem.message || problem.detail || problem.title || fallback, {
    status: response.status,
    code: problem.code,
    detail: problem.detail
  });
}

export function networkError(cause: unknown, fallback = '网络连接失败，请检查连接后重试。') {
  if (cause instanceof ApiError) return cause;
  const error = new ApiError(fallback, { kind: 'network' });
  if (cause instanceof Error) error.cause = cause;
  return error;
}

export function getUserFacingError(error: unknown, fallback = '请求暂时无法完成，请稍后重试。') {
  if (!(error instanceof ApiError)) return fallback;

  const defaults: Record<ApiErrorKind, string> = {
    authentication: '账号或密码不正确，请重新输入。',
    authorization: '当前账号没有执行此操作的权限。',
    validation: '提交的信息不符合要求，请检查后重试。',
    conflict: '该信息已被使用或与现有数据冲突。',
    'rate-limit': '操作过于频繁，请稍后再试。',
    server: '服务暂时不可用，请稍后重试。',
    network: '网络连接失败，请检查连接后重试。',
    unknown: fallback
  };

  return error.message || defaults[error.kind];
}
