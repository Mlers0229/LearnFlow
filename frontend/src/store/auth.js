import { ref, computed } from 'vue';

const STORAGE_KEY = 'learnflow_current_user';

function loadInitialUser() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error('load user from localStorage failed', e);
    return null;
  }
}

const currentUser = ref(loadInitialUser());

export function useAuthStore() {
  function setUser(user) {
    currentUser.value = user;
    if (user) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  }

  function logout() {
    setUser(null);
  }

  const isLoggedIn = computed(() => !!currentUser.value);
  const isAdmin = computed(
    () => currentUser.value && currentUser.value.role === 'admin'
  );

  return {
    currentUser,
    isLoggedIn,
    isAdmin,
    setUser,
    logout
  };
}


