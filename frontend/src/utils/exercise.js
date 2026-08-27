export function createExerciseDayState() {
  return {
    loading: false,
    error: '',
    items: [],
    loadedOnce: false,
    answers: [],
    results: [],
    submittingIndex: null,
    saveError: '',
    saveSuccessMessage: '',
    lastSubmittedIndex: null,
    lastSavedAt: ''
  };
}

export function scoreTagType(score) {
  if (score == null) return 'default';
  if (score >= 85) return 'success';
  if (score >= 60) return 'warning';
  return 'error';
}

export function formatMistakeType(type) {
  const map = {
    none: '回答准确',
    minor_gap: '轻微遗漏',
    partial_understanding: '部分理解',
    concept_gap: '概念缺口'
  };
  return map[type] || type || '';
}

export function isNeedsReview(record) {
  if (record?.aiScore == null) {
    return false;
  }
  return record.aiScore < 60 || record.aiMistakeType === 'concept_gap';
}
