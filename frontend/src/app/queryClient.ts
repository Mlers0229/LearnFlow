import { QueryClient } from '@tanstack/vue-query';

function shouldRetry(failureCount: number, error: unknown) {
  if (failureCount >= 2) return false;
  if (!(error instanceof Error)) return true;
  return !/\b(400|401|403|404|409|422)\b/.test(error.message);
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: shouldRetry,
      refetchOnWindowFocus: false
    },
    mutations: {
      retry: false
    }
  }
});
