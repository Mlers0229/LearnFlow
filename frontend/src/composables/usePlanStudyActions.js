import { reactive } from 'vue';
import {
  getResourcesByDay,
  getExercisesByDay,
  evaluateExerciseByDay,
  refineDay,
  updateDayStatus,
  saveExerciseRecord
} from '../api/plan';
import {
  createExerciseDayState,
  scoreTagType,
  formatMistakeType
} from '../utils/exercise';

function createResourceDayState() {
  return {
    loading: false,
    error: '',
    items: [],
    loadedOnce: false
  };
}

export function usePlanStudyActions(currentUser, options = {}) {
  const dayResourcesMap = reactive({});
  const dayStatusSavingMap = reactive({});
  const dayRefineMap = reactive({});
  const dayExercisesMap = reactive({});

  async function loadDayResources(day) {
    if (!day || !day.id) return;
    const dayId = day.id;

    if (!dayResourcesMap[dayId]) {
      dayResourcesMap[dayId] = createResourceDayState();
    }

    const state = dayResourcesMap[dayId];
    state.loading = true;
    state.error = '';
    state.items = [];

    try {
      const userId = currentUser.value ? currentUser.value.id : undefined;
      const items = await getResourcesByDay(dayId, userId);
      state.items = items || [];
      state.loadedOnce = true;
      if (typeof options.onDayResourcesLoaded === 'function') {
        options.onDayResourcesLoaded(state.items, day);
      }
    } catch (e) {
      console.error(e);
      state.error = '加载当日推荐资源失败，请稍后重试。';
    } finally {
      state.loading = false;
    }
  }

  async function loadDayExercises(day) {
    if (!day || !day.id) return;
    const dayId = day.id;

    if (!dayExercisesMap[dayId]) {
      dayExercisesMap[dayId] = createExerciseDayState();
    }

    const state = dayExercisesMap[dayId];
    state.loading = true;
    state.error = '';
    state.items = [];
    state.answers = [];
    state.results = [];
    state.submittingIndex = null;
    state.saveError = '';
    state.saveSuccessMessage = '';
    state.lastSubmittedIndex = null;

    try {
      const items = await getExercisesByDay(dayId);
      state.items = items || [];
      state.answers = state.items.map(() => '');
      state.results = state.items.map(() => null);
      state.loadedOnce = true;
    } catch (e) {
      console.error(e);
      state.error = '生成练习题失败，请稍后重试。';
    } finally {
      state.loading = false;
    }
  }

  async function markDayCompleted(day) {
    if (!day || !day.id) return;
    const dayId = day.id;

    if (!dayStatusSavingMap[dayId]) {
      dayStatusSavingMap[dayId] = {
        saving: false,
        error: ''
      };
    }

    const state = dayStatusSavingMap[dayId];
    state.saving = true;
    state.error = '';

    try {
      await updateDayStatus(dayId, 'completed');
      day.status = 'completed';
    } catch (e) {
      console.error(e);
      state.error = '更新学习状态失败，请稍后再试。';
    } finally {
      state.saving = false;
    }
  }

  async function refineDayTasks(day) {
    if (!day || !day.id) return;
    const dayId = day.id;

    if (!dayRefineMap[dayId]) {
      dayRefineMap[dayId] = {
        loading: false,
        error: ''
      };
    }

    const state = dayRefineMap[dayId];
    state.loading = true;
    state.error = '';

    try {
      const updated = await refineDay(dayId);
      day.tasks = updated.tasks || day.tasks;
      if (updated.title) {
        day.title = updated.title;
      }
      if (updated.status) {
        day.status = updated.status;
      }
    } catch (e) {
      console.error(e);
      state.error = '细化任务失败，请稍后再试。';
    } finally {
      state.loading = false;
    }
  }

  async function saveExerciseForDay(day, questionIndex) {
    if (!day || !day.id) return;

    const dayId = day.id;
    const state = dayExercisesMap[dayId];
    if (!state || !state.items || !state.items[questionIndex]) return;

    const answerText = state.answers[questionIndex];
    if (!answerText || !answerText.trim()) {
      state.saveError = '请先在上面的输入框写下你的答案。';
      state.saveSuccessMessage = '';
      return;
    }

    const q = state.items[questionIndex];

    state.submittingIndex = questionIndex;
    state.saveError = '';
    state.saveSuccessMessage = '';
    state.lastSubmittedIndex = null;

    try {
      const evaluation = await evaluateExerciseByDay(dayId, {
        question: q.question,
        referenceAnswer: q.answer,
        userAnswer: answerText.trim()
      });
      state.results[questionIndex] = evaluation;

      const userId = currentUser.value ? currentUser.value.id : undefined;
      await saveExerciseRecord(dayId, {
        userId,
        question: q.question,
        answer: q.answer,
        explanation: q.explanation,
        difficulty: q.difficulty,
        skillFocus: q.skillFocus,
        userAnswer: answerText.trim(),
        aiScore: evaluation?.score,
        aiMistakeType: evaluation?.mistakeType,
        aiFeedback: evaluation?.feedback,
        aiNextRecommendation: evaluation?.nextRecommendation
      });
      state.lastSavedAt = new Date().toLocaleTimeString();
      state.saveSuccessMessage = 'AI 评测已完成，练习记录也已成功保存。';
      state.lastSubmittedIndex = questionIndex;
    } catch (e) {
      console.error(e);
      state.saveError = state.results[questionIndex]
        ? 'AI 评测已完成，但保存练习记录失败，请稍后重试。'
        : '提交作答失败，请稍后重试。';
      state.lastSubmittedIndex = questionIndex;
    } finally {
      state.submittingIndex = null;
    }
  }

  function getExerciseResult(dayId, questionIndex) {
    return dayExercisesMap[dayId]?.results?.[questionIndex] || null;
  }

  function isExerciseSubmitting(dayId, questionIndex) {
    return dayExercisesMap[dayId]?.submittingIndex === questionIndex;
  }

  return {
    dayResourcesMap,
    dayStatusSavingMap,
    dayRefineMap,
    dayExercisesMap,
    loadDayResources,
    loadDayExercises,
    markDayCompleted,
    refineDayTasks,
    saveExerciseForDay,
    getExerciseResult,
    isExerciseSubmitting,
    scoreTagType,
    formatMistakeType
  };
}
