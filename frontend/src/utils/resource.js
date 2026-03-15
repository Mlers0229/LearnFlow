export function normalizeResourceFeedbackValue(resource) {
  if (!resource) return null;

  if (resource.currentUserFeedback === 'helpful' || resource.currentUserFeedback === 'invalid') {
    return resource.currentUserFeedback;
  }

  if (resource.currentUserReportedInvalid === true) {
    return 'invalid';
  }

  if (typeof resource.currentUserRating === 'number') {
    if (resource.currentUserRating >= 4) {
      return 'helpful';
    }
    if (resource.currentUserRating <= 2) {
      return 'invalid';
    }
  }

  return null;
}

export function formatResourceFeedbackLabel(value) {
  if (value === 'helpful') {
    return '有帮助';
  }
  if (value === 'invalid') {
    return '不相关 / 无效';
  }
  return '';
}

export function buildResourceQualityParts(resource) {
  if (!resource) return [];

  const parts = [];
  if (typeof resource.avgRating === 'number' && Number.isFinite(resource.avgRating)) {
    parts.push(`平均 ${resource.avgRating.toFixed(1)} 分`);
  }
  if (typeof resource.feedbackCount === 'number' && resource.feedbackCount > 0) {
    parts.push(`${resource.feedbackCount} 条反馈`);
  }
  if (typeof resource.invalidReportCount === 'number' && resource.invalidReportCount > 0) {
    parts.push(`${resource.invalidReportCount} 次无效反馈`);
  }

  return parts;
}
