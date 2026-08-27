<template>
  <div class="lf-admin-shell">
    <a class="lf-admin-skip" href="#admin-main">跳到主要内容</a>

    <aside class="lf-admin-sidebar" aria-label="管理控制台导航">
      <AppBrand to="/admin" subtitle="管理控制台" inverse />

      <div class="lf-admin-environment">
        <span><i aria-hidden="true" />控制台在线</span>
        <small>Production governance</small>
      </div>

      <div class="lf-admin-nav-label">平台治理</div>
      <AppNavigation :items="navItems" label="管理端主导航" />

      <div class="lf-admin-sidebar-spacer" />

      <RouterLink to="/" class="lf-admin-back">
        <ArrowLeft :size="17" aria-hidden="true" />
        返回学习工作台
      </RouterLink>

      <div class="lf-admin-account">
        <div class="lf-admin-avatar">{{ userInitial }}</div>
        <div>
          <strong>{{ currentUser?.username || '管理员' }}</strong>
          <span>Administrator</span>
        </div>
        <n-button quaternary circle aria-label="退出登录" @click="confirmLogout">
          <template #icon><LogOut /></template>
        </n-button>
      </div>
    </aside>

    <section class="lf-admin-stage">
      <header class="lf-admin-topbar">
        <n-button class="lf-admin-mobile-menu" quaternary circle aria-label="打开管理导航" @click="drawerOpen = true">
          <template #icon><Menu /></template>
        </n-button>
        <div>
          <div class="lf-admin-eyebrow">Admin control center</div>
          <div class="lf-admin-title-row">
            <h1>{{ currentAdminPageLabel }}</h1>
            <span>平台治理视图</span>
          </div>
        </div>
        <div class="lf-admin-topbar-meta">
          <div><small>当前角色</small><strong>管理员</strong></div>
          <div><small>环境</small><strong>Staging</strong></div>
        </div>
      </header>

      <main id="admin-main" class="lf-admin-main" tabindex="-1">
        <RouterView />
      </main>

      <footer class="lf-admin-footer">
        <span>LearnFlow Admin · 操作应保留审计证据</span>
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">津ICP备2024026404号-2</a>
      </footer>
    </section>

    <n-drawer v-model:show="drawerOpen" placement="left" :width="300">
      <n-drawer-content closable title="管理控制台">
        <div class="lf-admin-drawer">
          <AppBrand to="/admin" subtitle="管理控制台" />
          <AppNavigation :items="navItems" label="管理端主导航" @navigate="drawerOpen = false" />
          <RouterLink to="/" class="lf-admin-back lf-admin-back--light" @click="drawerOpen = false">
            <ArrowLeft :size="17" />返回学习工作台
          </RouterLink>
        </div>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import { useDialog } from 'naive-ui';
import {
  Activity,
  ArrowLeft,
  Bot,
  LayoutDashboard,
  LibraryBig,
  LogOut,
  Menu,
  Users
} from 'lucide-vue-next';
import AppBrand from '../shared/components/AppBrand.vue';
import AppNavigation, { type NavigationItem } from '../shared/components/AppNavigation.vue';
import { useAuthStore } from '../store/auth';

const navItems: NavigationItem[] = [
  { to: '/admin', label: '总览', icon: LayoutDashboard, exact: true },
  { to: '/admin/resources', label: '资源管理', icon: LibraryBig },
  { to: '/admin/models', label: '模型配置', icon: Bot },
  { to: '/admin/logs', label: '调用日志', icon: Activity },
  { to: '/admin/users', label: '用户管理', icon: Users }
];

const route = useRoute();
const router = useRouter();
const dialog = useDialog();
const drawerOpen = ref(false);
const { currentUser, logout } = useAuthStore();

const currentAdminPageLabel = computed(() => {
  const matched = navItems.find((item) =>
    item.exact ? route.path === item.to : route.path === item.to || route.path.startsWith(`${item.to}/`)
  );
  return matched?.label || '总览';
});

const userInitial = computed(() => String(currentUser.value?.username || 'A').slice(0, 1).toUpperCase());

watch(() => route.fullPath, () => {
  drawerOpen.value = false;
});

const confirmLogout = () => {
  dialog.warning({
    title: '退出管理控制台？',
    content: '请确认当前配置和批量操作已经保存。',
    positiveText: '退出登录',
    negativeText: '继续管理',
    onPositiveClick: () => {
      logout();
      return router.push('/login');
    }
  });
};
</script>

<style scoped>
.lf-admin-shell {
  display: grid;
  min-height: 100vh;
  grid-template-columns: 276px minmax(0, 1fr);
  background: #eef2f4;
}

.lf-admin-skip {
  position: fixed;
  z-index: 999;
  top: 10px;
  left: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #ffffff;
  color: #102733;
  transform: translateY(-150%);
}

.lf-admin-skip:focus { transform: translateY(0); }

.lf-admin-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  height: 100vh;
  min-width: 0;
  flex-direction: column;
  padding: 24px 18px 18px;
  background:
    radial-gradient(circle at 0 0, rgba(72, 175, 168, 0.13), transparent 32%),
    linear-gradient(180deg, #112630, #0a1c25);
  color: #e9f1f3;
}

.lf-admin-environment {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 28px 4px 24px;
  padding: 12px;
  border: 1px solid rgba(148, 184, 191, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.045);
}

.lf-admin-environment span { display: flex; align-items: center; gap: 8px; font-size: 12px; font-weight: 680; }
.lf-admin-environment i { width: 7px; height: 7px; border-radius: 50%; background: #46d49b; box-shadow: 0 0 0 4px rgba(70, 212, 155, 0.1); }
.lf-admin-environment small { color: rgba(217, 232, 235, 0.55); font-size: 10px; }

.lf-admin-nav-label {
  margin: 0 12px 8px;
  color: rgba(217, 232, 235, 0.52);
  font-size: 10px;
  font-weight: 760;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.lf-admin-sidebar :deep(.lf-navigation-link) { color: rgba(237, 245, 246, 0.74); }
.lf-admin-sidebar :deep(.lf-navigation-link:hover) { background: rgba(255, 255, 255, 0.07); color: #ffffff; }
.lf-admin-sidebar :deep(.lf-navigation-link--active) { background: linear-gradient(135deg, #b96d29, #d58b43); color: #ffffff; box-shadow: 0 12px 24px rgba(0, 0, 0, 0.2); }

.lf-admin-sidebar-spacer { flex: 1; }

.lf-admin-back {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  color: rgba(229, 239, 241, 0.7);
  font-size: 12px;
  font-weight: 640;
  text-decoration: none;
}

.lf-admin-back:hover { background: rgba(255, 255, 255, 0.06); color: #ffffff; }
.lf-admin-back--light { color: var(--lf-text-default); }

.lf-admin-account {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 11px;
  border: 1px solid rgba(148, 184, 191, 0.12);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.05);
}

.lf-admin-avatar { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 12px; background: rgba(213, 139, 67, 0.18); color: #ffd5aa; font-weight: 780; }
.lf-admin-account > div:nth-child(2) { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.lf-admin-account strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.lf-admin-account span { color: rgba(217, 232, 235, 0.52); font-size: 9px; text-transform: uppercase; }

.lf-admin-stage { display: flex; min-width: 0; min-height: 100vh; flex-direction: column; }
.lf-admin-topbar { position: sticky; z-index: var(--lf-z-sticky); top: 0; display: flex; min-height: 82px; align-items: center; justify-content: space-between; gap: 20px; padding: 14px clamp(20px, 3vw, 44px); border-bottom: 1px solid rgba(27, 54, 66, 0.11); background: rgba(238, 242, 244, 0.88); backdrop-filter: blur(18px); }
.lf-admin-mobile-menu { display: none; }
.lf-admin-eyebrow { color: #8a551d; font-size: 10px; font-weight: 760; letter-spacing: 0.14em; text-transform: uppercase; }
.lf-admin-title-row { display: flex; align-items: center; gap: 12px; margin-top: 3px; }
.lf-admin-title-row h1 { margin: 0; color: #102733; font-size: 20px; letter-spacing: -0.025em; }
.lf-admin-title-row span { padding: 4px 8px; border-radius: 999px; background: rgba(185, 109, 41, 0.1); color: #8a551d; font-size: 10px; font-weight: 680; }
.lf-admin-topbar-meta { display: flex; gap: 8px; }
.lf-admin-topbar-meta > div { display: flex; min-width: 88px; flex-direction: column; gap: 2px; padding: 8px 10px; border: 1px solid rgba(27, 54, 66, 0.1); border-radius: 12px; background: rgba(255, 255, 255, 0.64); }
.lf-admin-topbar-meta small { color: #687c84; font-size: 9px; text-transform: uppercase; }
.lf-admin-topbar-meta strong { color: #233e49; font-size: 11px; }

.lf-admin-main { width: min(100%, 1600px); flex: 1; align-self: center; padding: clamp(20px, 3vw, 42px); outline: none; }
.lf-admin-footer { display: flex; justify-content: space-between; gap: 16px; padding: 18px clamp(20px, 3vw, 44px) 26px; color: #61767e; font-size: 11px; }
.lf-admin-footer a { color: inherit; text-decoration: none; }
.lf-admin-drawer { display: flex; min-height: calc(100vh - 90px); flex-direction: column; gap: 24px; }

@media (max-width: 1024px) {
  .lf-admin-shell { grid-template-columns: 1fr; }
  .lf-admin-sidebar { display: none; }
  .lf-admin-mobile-menu { display: inline-flex; }
}

@media (max-width: 640px) {
  .lf-admin-topbar { min-height: 70px; padding: 10px 14px; gap: 10px; }
  .lf-admin-title-row h1 { font-size: 17px; }
  .lf-admin-title-row span, .lf-admin-topbar-meta { display: none; }
  .lf-admin-main { padding: 16px 12px 28px; }
  .lf-admin-footer { flex-direction: column; padding: 14px 16px 22px; }
}
</style>
