import { reactive } from 'vue';
import { submitResourceFeedback } from '../api/resource';
import {
  formatResourceFeedbackLabel,
  normalizeResourceFeedbackValue
} from '../utils/resource';

export function useResourceFeedback(currentUser) {
  const resourceFeedbackState = reactive({});

  function ensureFeedbackState(resourceId) {
    if (!resourceFeedbackState[resourceId]) {
      resourceFeedbackState[resourceId] = {
        value: null,
        loading: false
      };
    }
    return resourceFeedbackState[resourceId];
  }

  function hydrateResourceFeedback(items = []) {
    items.forEach((resource) => {
      if (!resource?.id) return;
      const state = ensureFeedbackState(resource.id);
      const inferredValue = normalizeResourceFeedbackValue(resource);
      if (inferredValue) {
        state.value = inferredValue;
      }
    });
    return items;
  }

  async function sendResourceFeedback(resource, value) {
    if (!resource?.id || !value) return;

    const state = ensureFeedbackState(resource.id);
    const userId = currentUser.value ? currentUser.value.id : undefined;
    state.loading = true;

    try {
      await submitResourceFeedback(resource.id, {
        userId,
        rating: value === 'helpful' ? 5 : 1,
        comment: null,
        reportedInvalid: value === 'invalid'
      });

      state.value = value;
      resource.currentUserFeedback = value;
      resource.currentUserRating = value === 'helpful' ? 5 : 1;
      resource.currentUserReportedInvalid = value === 'invalid';
    } catch (e) {
      console.error('submit resource feedback failed', e);
      throw e;
    } finally {
      state.loading = false;
    }
  }

  function getFeedbackState(resourceId) {
    return ensureFeedbackState(resourceId);
  }

  return {
    resourceFeedbackState,
    hydrateResourceFeedback,
    sendResourceFeedback,
    getFeedbackState,
    formatResourceFeedbackLabel
  };
}
