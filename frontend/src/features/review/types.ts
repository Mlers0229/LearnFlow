export type ReviewStatus = 'all' | 'needs_review' | 'mastered' | 'unscored';

export type ExerciseRecord = {
  id: number | string;
  planId?: number | string;
  planTitle?: string;
  dayId?: number | string;
  dayDate?: string;
  dayTitle?: string;
  question?: string;
  referenceAnswer?: string;
  explanation?: string;
  difficulty?: string;
  skillFocus?: string;
  userAnswer?: string;
  aiScore?: number | null;
  aiMistakeType?: string;
  aiFeedback?: string;
  aiNextRecommendation?: string;
  isCorrect?: boolean | null;
  createdAt?: string;
};

export type ReviewSummary = {
  totalRecords: number;
  scoredRecords: number;
  averageScore: number | null;
  highestScore: number | null;
  latestScore: number | null;
  masteredCount: number;
  needsReviewCount: number;
};

export type ReviewGroup = {
  key: string;
  planId?: number | string;
  dayId?: number | string;
  planTitle?: string;
  dayTitle?: string;
  dayDate?: string;
  records: ExerciseRecord[];
};

export type MasteryEvidence = {
  eventId: number | string;
  eventType?: string;
  sourceType?: string;
  sourceId?: number | string;
  signalValue?: number;
  signalWeight?: number;
  summary?: string;
  occurredAt?: string;
};

export type MasteryProfile = {
  knowledgePointId?: number | string;
  knowledgeKey: string;
  displayName?: string;
  masteryScore?: number;
  confidence?: number;
  effectiveWeight?: number;
  sampleCount?: number;
  algorithmVersion?: string;
  calculatedAt?: string;
  evidence?: MasteryEvidence[];
};

export type WeakSkill = {
  name: string;
  attempts: number;
  needsReview: number;
  averageScore: number | null;
};
