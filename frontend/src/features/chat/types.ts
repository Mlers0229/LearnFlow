export type ChatMessageRole = 'user' | 'assistant';

export type ChatMessageStatus = 'complete' | 'streaming' | 'stopped' | 'error';

export type ChatSource = {
  title: string;
  url: string;
  hostname: string;
};

export type ChatMessage = {
  id: string;
  role: ChatMessageRole;
  content: string;
  createdAt: Date;
  status: ChatMessageStatus;
  error?: string;
};

export type ContextResource = {
  id?: number | string;
  title?: string;
  url?: string;
  reason?: string;
  domain?: string;
  sourceType?: string;
  ingestionStatus?: string;
};

export type ContextDay = {
  id: number | string;
  date?: string;
  title?: string;
  tasks?: string[];
};

export type ContextPlan = {
  id: number | string;
  title?: string;
  days?: ContextDay[];
};
