<template>
  <div>
    <header class="app-header app-header-admin">
      <div class="app-header-inner app-header-inner-admin">
        <div class="brand-cluster brand-cluster-admin">
          <div class="logo" @click="goAdminHome">
            <span class="logo-dot" />
            <span class="logo-text">LearnFlow 管理端</span>
          </div>
          <div class="brand-copy brand-copy-admin">
            <div class="brand-kicker">Admin Control Center</div>
            <div class="brand-title-row">
              <div class="brand-admin-title">运营控制台</div>
              <span class="brand-admin-badge">Console</span>
            </div>
            <div class="brand-caption">审核资源、管理模型、查看调用日志并管理平台用户</div>
            <div class="brand-admin-meta">
              <span class="header-chip header-chip-admin-page">{{ currentAdminPageLabel }}</span>
              <span class="header-chip header-chip-admin-subtle">平台治理视图</span>
            </div>
          </div>
        </div>

        <div class="header-center">
          <div class="nav-shell nav-shell-admin">
            <nav class="nav">
              <RouterLink
                v-for="item in navItems"
                :key="item.to"
                :to="item.to"
                :class="['nav-link', isAdminNavActive(item.to) && 'nav-link-active']"
              >
                <span class="nav-link-label">{{ item.label }}</span>
                <span v-if="isAdminNavActive(item.to)" class="nav-link-indicator">当前</span>
              </RouterLink>
            </nav>
          </div>
        </div>

        <div class="user-info">
          <div class="user-panel user-panel-admin">
            <div class="user-summary">
              <div class="user-summary-kicker">控制台状态</div>
              <div class="user-name-row">
                <div class="user-name">{{ currentUser?.username || '管理员' }}</div>
                <span class="header-chip header-chip-admin-page">{{ currentAdminPageLabel }}</span>
              </div>
              <div class="user-summary-meta">
                <span class="user-role-tag">admin</span>
                <span class="header-chip">控制台在线</span>
              </div>
            </div>
            <div class="user-panel-actions">
              <RouterLink to="/" class="nav-link nav-link-quiet nav-link-admin-back">返回用户端</RouterLink>
              <button class="btn-ghost" type="button" @click="handleLogout">
                退出登录
              </button>
            </div>
          </div>
        </div>
      </div>
    </header>

    <main class="app-main">
      <div class="app-main-inner">
        <div class="page-layout">
          <RouterView />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../store/auth';

const navItems = [
  { to: '/admin', label: '\u603b\u89c8' },
  { to: '/admin/resources', label: '\u8d44\u6e90\u7ba1\u7406' },
  { to: '/admin/models', label: '\u6a21\u578b\u914d\u7f6e' },
  { to: '/admin/logs', label: '\u8c03\u7528\u65e5\u5fd7' },
  { to: '/admin/users', label: '\u7528\u6237\u7ba1\u7406' }
];

const route = useRoute();
const router = useRouter();
const { currentUser, logout } = useAuthStore();

const currentAdminPageLabel = computed(() => {
  const matched = navItems.find((item) => {
    if (item.to === '/admin') return route.path === '/admin';
    return route.path.startsWith(item.to);
  });
  return matched?.label || '\u603b\u89c8';
});

const isAdminNavActive = (to) => {
  if (to === '/admin') return route.path === '/admin';
  return route.path.startsWith(to);
};

const goAdminHome = () => {
  router.push('/admin');
};

const handleLogout = () => {
  if (window.confirm('确定要退出登录吗？')) {
    logout();
    router.push('/login');
  }
};
</script>
