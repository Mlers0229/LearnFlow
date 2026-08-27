export type PlanLevel = 'beginner' | 'intermediate' | 'advanced';

export type PreferredStyle =
  | 'balanced'
  | 'practice_first'
  | 'theory_first'
  | 'exercise_driven';

export type PlanFormValue = {
  goalText: string;
  durationWeeks: number;
  hoursPerDay: number;
  level: PlanLevel;
  targetRole: string;
  preferredStyle: PreferredStyle;
  finalDeliverable: string;
  constraintsText: string;
};

export type PlanPayload = {
  goalText: string;
  durationWeeks: number;
  hoursPerDay: number;
  level: PlanLevel;
  targetRole: string | null;
  preferredStyle: PreferredStyle;
  finalDeliverable: string | null;
  constraints: string[];
};

export type PlanRecord = Record<string, unknown> & {
  id?: number | string;
  createdAt?: string;
  updatedAt?: string;
};

export type PlanTaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'PAUSED'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED';

export type PlanTask = {
  id: number | string;
  status: PlanTaskStatus;
  progress?: number;
  resultResourceId?: number | string | null;
  errorCode?: string | null;
};

export type PlanGenerationPhase =
  | 'idle'
  | 'creating'
  | 'running'
  | 'paused'
  | 'cancelling'
  | 'cancelled'
  | 'succeeded'
  | 'failed';
