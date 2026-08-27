import { http, HttpResponse } from 'msw';

export const handlers = [
  http.post('*/api/auth/refresh', () =>
    HttpResponse.json({ message: '测试环境未登录' }, { status: 401 })
  )
];
