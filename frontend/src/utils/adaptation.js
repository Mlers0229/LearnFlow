function field(value, camel, snake) {
  return value?.[camel] ?? value?.[snake];
}

export function describeAdaptation(adaptation, { detailed = true } = {}) {
  if (!adaptation) return '';
  const reason = field(adaptation, 'reason', 'reason');
  const version = field(adaptation, 'policyVersion', 'policy_version') || 'adaptive-v1';
  if (reason === 'control') return `固定策略对照 · ${version}`;
  if (reason === 'insufficient_evidence') return '掌握度证据不足，保持原计划难度与节奏。';
  if (!adaptation.applied) return '自适应策略当前未应用，已安全回退到固定策略。';
  if (!detailed) return `掌握度自适应已应用 · ${version}`;
  const difficulty = field(adaptation, 'targetDifficulty', 'target_difficulty') || '当前';
  const interval = field(adaptation, 'reviewIntervalDays', 'review_interval_days');
  const priority = field(adaptation, 'reviewPriority', 'review_priority') || '常规';
  return `目标难度 ${difficulty} · 复习优先级 ${priority}${interval ? ` · 建议间隔 ${interval} 天` : ''}`;
}
