import { ref, computed } from 'vue';
import { logoutSession, restoreSession } from '../api/auth';
import { clearAccessToken, onAuthenticationFailure, setAccessToken } from '../api/client';

/** @typedef {{ username?: string, role?: string, [key: string]: unknown }} AuthUser */

/** @type {import('vue').Ref<AuthUser | null>} */
const currentUser = ref(null);
let initializePromise = null;

function toUser(session) {
  if (!session) return null;
  const user = { ...session };
  delete user.accessToken;
  delete user.expiresInSeconds;
  return user;
}

function clearSession() {
  clearAccessToken();
  currentUser.value = null;
}

onAuthenticationFailure(clearSession);

export function useAuthStore() {
  function setUser(user) {
    if (user && Object.prototype.hasOwnProperty.call(user, 'accessToken')) {
      setAccessToken(user.accessToken);
    }
    currentUser.value = toUser(user);
  }

  function logout() {
    void logoutSession();
    clearSession();
  }

  function initialize() {
    if (!initializePromise) {
      initializePromise = restoreSession()
        .then((session) => {
          setUser(session);
          return currentUser.value;
        })
        .catch(() => {
          clearSession();
          return null;
        });
    }
    return initializePromise;
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
    logout,
    initialize
  };
}


