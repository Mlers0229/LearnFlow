<template>
  <n-layout class="app-layout">
    <n-layout-header bordered class="app-header app-header-user">
      <div class="app-header-inner app-header-inner-user">
        <div class="brand-cluster">
          <div class="logo" @click="goHome">
            <span class="logo-dot" />
            <span class="logo-text">
              <span class="logo-text-main">LearnFlow</span>
              <span class="logo-text-sub">智能学习系统</span>
            </span>
          </div>
          <div class="brand-copy">
            <div class="brand-kicker">Learning Studio</div>
            <div class="brand-caption">{{ headerCaption }}</div>
          </div>
        </div>

        <div class="header-center">
          <div class="nav-shell">
            <nav class="nav">
              <RouterLink
                v-for="item in mainNavItems"
                :key="item.to"
                :to="item.to"
                :class="['nav-link', isNavActive(item.to) && 'nav-link-active']"
              >
                <span class="nav-link-label">{{ item.label }}</span>
                <span v-if="isNavActive(item.to)" class="nav-link-indicator">当前</span>
              </RouterLink>

              <RouterLink
                to="/upload-resource"
                :class="['nav-link', 'nav-link-upload', isNavActive('/upload-resource') && 'nav-link-active']"
              >
                <span class="nav-link-label">上传学习资源</span>
                <span v-if="isNavActive('/upload-resource')" class="nav-link-indicator">当前</span>
              </RouterLink>

              <RouterLink
                v-if="isAdmin"
                to="/admin/resources"
                :class="['nav-link', isAdminSectionActive && 'nav-link-active']"
              >
                <span class="nav-link-label">管理端</span>
                <span v-if="isAdminSectionActive" class="nav-link-indicator">当前</span>
              </RouterLink>

              <n-dropdown
                v-if="isAdmin"
                trigger="hover"
                :options="devMenuOptions"
                @select="handleDevSelect"
              >
                <button type="button" class="nav-link nav-link-more">
                  <span class="nav-link-label">开发者</span>
                </button>
              </n-dropdown>
            </nav>
          </div>
        </div>

        <div class="user-info">
          <template v-if="isLoggedIn">
            <div class="user-panel">
              <div class="user-summary">
                <div class="user-summary-kicker">当前工作区</div>
                <div class="user-name-row">
                  <div class="user-name">你好，{{ currentUser?.username }}</div>
                  <span class="header-chip header-chip-page">{{ currentPageLabel }}</span>
                </div>
                <div class="user-summary-meta">
                  <span class="user-role-tag">{{ userRoleLabel }}</span>
                  <span class="header-chip header-chip-muted">{{ loginStatusLabel }}</span>
                </div>
              </div>
              <div class="user-panel-actions">
                <RouterLink to="/profile" class="nav-link nav-link-quiet">个人设置</RouterLink>
                <button class="btn-ghost" type="button" @click="handleLogout">
                  退出登录
                </button>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="user-panel user-panel-guest">
              <div class="user-summary">
                <div class="user-summary-kicker">欢迎来到 LearnFlow</div>
                <div class="brand-caption">登录后即可开始生成计划、复盘练习与追踪进度</div>
              </div>
              <div class="user-panel-actions">
                <RouterLink to="/login" class="nav-link">登录</RouterLink>
                <RouterLink to="/register" class="nav-link nav-link-active-soft">注册</RouterLink>
              </div>
            </div>
          </template>
        </div>
      </div>
    </n-layout-header>

    <n-layout-content class="app-main">
      <div class="app-main-inner">
        <div class="page-layout">
          <RouterView />
        </div>
        <footer class="site-footer">
          <a
            href="https://beian.miit.gov.cn/"
            target="_blank"
            rel="noopener noreferrer"
            class="site-footer-link"
          >
            津ICP备2024026404号-2
          </a>
        </footer>
      </div>
    </n-layout-content>
  </n-layout>
</template>

<script setup>
import { computed } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../store/auth';

const mainNavItems = [
  { to: '/', label: '生成学习计划' },
  { to: '/history', label: '历史计划' },
  { to: '/exercise-review', label: '练习回顾' },
  { to: '/chat', label: 'AI 对话' },
  { to: '/about', label: '关于系统' }
];

const devMenuOptions = [
  {
    key: '/debug/agent-logs',
    label: 'Agent 调用日志（调试）'
  }
];

const route = useRoute();
const router = useRouter();
const { currentUser, isLoggedIn, isAdmin, logout } = useAuthStore();

const userRoleLabel = computed(() => (isAdmin.value ? 'admin' : 'student'));
const loginStatusLabel = computed(() => (isAdmin.value ? '管理能力已开启' : '学习进行中'));
const headerCaption = computed(() =>
  isLoggedIn.value
    ? '把计划、练习回顾、AI 对话和资源沉淀放进同一个学习工作台'
    : '登录后即可进入你的个人学习工作台'
);

const currentPageLabel = computed(() => {
  if (route.path.startsWith('/history')) return '历史计划';
  if (route.path.startsWith('/exercise-review')) return '练习回顾';
  if (route.path.startsWith('/chat')) return 'AI 对话';
  if (route.path.startsWith('/about')) return '关于系统';
  if (route.path.startsWith('/upload-resource')) return '资源上传';
  if (route.path.startsWith('/profile')) return '个人设置';
  if (route.path.startsWith('/debug/agent-logs')) return '调试日志';
  return '生成学习计划';
});

const isAdminSectionActive = computed(() => route.path.startsWith('/admin'));

const isNavActive = (to) => {
  if (to === '/') return route.path === '/';
  return route.path.startsWith(to);
};

const handleLogout = () => {
  if (window.confirm('确定要退出登录吗？')) {
    logout();
    router.push('/login');
  }
};

const goHome = () => {
  router.push('/');
};

const handleDevSelect = (path) => {
  if (path) {
    router.push(path);
  }
};
</script>

<style scoped>
.site-footer {
  display: flex;
  justify-content: center;
  padding: 22px 0 8px;
}

.site-footer-link {
  color: #738496;
  font-size: 12px;
  letter-spacing: 0.02em;
  text-decoration: none;
  transition: color 0.16s ease;
}

.site-footer-link:hover {
  color: #1f5f68;
}
</style>
