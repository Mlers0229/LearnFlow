import { afterAll, afterEach, beforeAll } from 'vitest';
import { testServer } from './server';

beforeAll(() => testServer.listen({ onUnhandledRequest: 'error' }));
afterEach(() => testServer.resetHandlers());
afterAll(() => testServer.close());
