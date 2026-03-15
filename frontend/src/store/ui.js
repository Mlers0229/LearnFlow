import { computed, ref, watch } from 'vue';

const THEME_KEY = 'learnflow_theme';
const FONT_KEY = 'learnflow_font_scale';

const theme = ref(localStorage.getItem(THEME_KEY) || 'light');
const fontScale = ref(parseFloat(localStorage.getItem(FONT_KEY) || '1') || 1);

watch(theme, (val) => {
  document.documentElement.setAttribute('data-theme', val);
  localStorage.setItem(THEME_KEY, val);
});

watch(fontScale, (val) => {
  const scale = Math.min(Math.max(val, 0.9), 1.3);
  document.documentElement.style.fontSize = `${14 * scale}px`;
  localStorage.setItem(FONT_KEY, String(scale));
});

// 初始化
document.documentElement.setAttribute('data-theme', theme.value);
document.documentElement.style.fontSize = `${14 * fontScale.value}px`;

export function useUiStore() {
  const isDark = computed(() => theme.value === 'dark');

  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark';
  }

  function increaseFont() {
    fontScale.value = Math.min(fontScale.value + 0.05, 1.3);
  }

  function decreaseFont() {
    fontScale.value = Math.max(fontScale.value - 0.05, 0.9);
  }

  return {
    theme,
    fontScale,
    isDark,
    toggleTheme,
    increaseFont,
    decreaseFont
  };
}


