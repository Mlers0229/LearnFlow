<template>
  <div class="lf-workspace-shell">
    <a class="lf-skip-link" href="#main-content">跳到主要内容</a>

    <aside class="lf-workspace-sidebar" aria-label="学习工作台导航">
      <AppBrand class="lf-workspace-brand" />

      <div class="lf-sidebar-section">
        <div class="lf-sidebar-label">学习空间</div>
        <AppNavigation :items="primaryItems" />
      </div>

      <div class="lf-sidebar-section">
        <div class="lf-sidebar-label">资源与账号</div>
        <AppNavigation :items="secondaryItems" label="资源与账号导航" />
      </div>

      <RouterLink v-if="isAdmin" to="/admin" class="lf-admin-entry">
        <ShieldCheck :size="19" aria-hidden="true" />
        <span>
          <strong>进入管理控制台</strong>
          <small>资源、模型与平台治理</small>
        </span>
        <ChevronRight :size="16" aria-hidden="true" />
      </RouterLink>

      <div class="lf-sidebar-spacer" />

      <div class="lf-sidebar-account">
        <div class="lf-user-avatar" aria-hidden="true">{{ userInitial }}</div>
        <div class="lf-user-copy">
          <strong>{{ currentUser?.username || '学习者' }}</strong>
          <span>{{ isAdmin ? '管理员 · 学习视图' : '个人学习空间' }}</span>
        </div>
        <n-button quaternary circle aria-label="退出登录" @click="confirmLogout">
          <template #icon><LogOut /></template>
        </n-button>
      </div>
    </aside>

    <section class="lf-workspace-stage">
      <header class="lf-workspace-topbar">
        <n-button class="lf-mobile-menu" quaternary circle aria-label="打开导航" @click="drawerOpen = true">
          <template #icon><Menu /></template>
        </n-button>

        <div class="lf-page-context">
          <div class="lf-page-eyebrow">{{ currentSection }}</div>
          <div class="lf-page-title-row">
            <h1>{{ currentPageLabel }}</h1>
            <span class="lf-live-status"><i aria-hidden="true" />学习空间已同步</span>
          </div>
        </div>

        <div class="lf-topbar-actions">
          <n-tooltip trigger="hover">
            <template #trigger>
              <n-button quaternary circle aria-label="打开个人设置" @click="router.push('/profile')">
                <template #icon><Settings /></template>
              </n-button>
            </template>
            个人设置
          </n-tooltip>
          <div class="lf-topbar-user">
            <span>{{ currentUser?.username || '学习者' }}</span>
            <small>{{ isAdmin ? 'admin' : 'student' }}</small>
          </div>
        </div>
      </header>

      <main id="main-content" class="lf-workspace-main" tabindex="-1">
        <RouterView />
      </main>

      <footer class="lf-workspace-footer">
        <span>LearnFlow · 让每次学习都有下一步</span>
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">津ICP备2024026404号-2</a>
      </footer>
    </section>

    <n-drawer v-model:show="drawerOpen" placement="left" :width="300">
      <n-drawer-content closable title="学习工作台">
        <div class="lf-drawer-content">
          <AppBrand />
          <div class="lf-sidebar-section">
            <div class="lf-sidebar-label">学习空间</div>
            <AppNavigation :items="primaryItems" @navigate="drawerOpen = false" />
          </div>
          <div class="lf-sidebar-section">
            <div class="lf-sidebar-label">资源与账号</div>
            <AppNavigation :items="secondaryItems" label="资源与账号导航" @navigate="drawerOpen = false" />
          </div>
          <RouterLink v-if="isAdmin" to="/admin" class="lf-admin-entry" @click="drawerOpen = false">
            <ShieldCheck :size="19" />
            <span><strong>进入管理控制台</strong><small>平台治理视图</small></span>
            <ChevronRight :size="16" />
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
  BookCheck,
  ChevronRight,
  CircleHelp,
  History,
  LogOut,
  Menu,
  MessagesSquare,
  Settings,
  ShieldCheck,
  Sparkles,
  UploadCloud,
  UserRound
} from 'lucide-vue-next';
import AppBrand from '../shared/components/AppBrand.vue';
import AppNavigation, { type NavigationItem } from '../shared/components/AppNavigation.vue';
import { useAuthStore } from '../store/auth';

const primaryItems: NavigationItem[] = [
  { to: '/', label: '生成学习计划', icon: Sparkles, exact: true },
  { to: '/history', label: '历史计划', icon: History },
  { to: '/exercise-review', label: '练习回顾', icon: BookCheck },
  { to: '/chat', label: 'AI 对话', icon: MessagesSquare }
];

const secondaryItems: NavigationItem[] = [
  { to: '/upload-resource', label: '上传学习资源', icon: UploadCloud },
  { to: '/profile', label: '个人设置', icon: UserRound },
  { to: '/about', label: '关于 LearnFlow', icon: CircleHelp }
];

const pageMeta = [
  { prefix: '/history', title: '历史学习计划', section: '执行与复盘' },
  { prefix: '/exercise-review', title: '练习回顾', section: '练习与掌握度' },
  { prefix: '/chat', title: 'AI 对话陪练', section: '智能辅学' },
  { prefix: '/upload-resource', title: '上传学习资源', section: '资源共建' },
  { prefix: '/profile', title: '个人设置', section: '账号与隐私' },
  { prefix: '/about', title: '关于 LearnFlow', section: '产品信息' }
];

const route = useRoute();
const router = useRouter();
const dialog = useDialog();
const drawerOpen = ref(false);
const { currentUser, isAdmin, logout } = useAuthStore();

const activeMeta = computed(() => pageMeta.find((item) => route.path.startsWith(item.prefix)));
const currentPageLabel = computed(() => activeMeta.value?.title || '生成学习计划');
const currentSection = computed(() => activeMeta.value?.section || '学习计划');
const userInitial = computed(() => String(currentUser.value?.username || 'L').slice(0, 1).toUpperCase());

watch(() => route.fullPath, () => {
  drawerOpen.value = false;
});

const confirmLogout = () => {
  dialog.warning({
    title: '退出 LearnFlow？',
    content: '当前页面中尚未提交的输入不会保存。',
    positiveText: '退出登录',
    negativeText: '继续学习',
    onPositiveClick: () => {
      logout();
      return router.push('/login');
    }
  });
};
</script>

<style scoped>
.lf-workspace-shell {
  display: grid;
  min-height: 100vh;
  grid-template-columns: 272px minmax(0, 1fr);
  background: var(--lf-bg-canvas);
}

.lf-skip-link {
  position: fixed;
  z-index: 999;
  top: 10px;
  left: 10px;
  padding: 10px 14px;
  border-radius: var(--lf-radius-md);
  background: var(--lf-brand-800);
  color: #fff;
  transform: translateY(-150%);
}

.lf-skip-link:focus { transform: translateY(0); }

.lf-workspace-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  height: 100vh;
  min-width: 0;
  flex-direction: column;
  padding: 24px 18px 18px;
  border-right: 1px solid var(--lf-border-subtle);
  background: rgba(251, 253, 253, 0.92);
  backdrop-filter: blur(18px);
}

.lf-workspace-brand { margin: 0 6px 30px; }
.lf-sidebar-section + .lf-sidebar-section { margin-top: 22px; }

.lf-sidebar-label {
  margin: 0 12px 8px;
  color: var(--lf-text-subtle);
  font-size: 10px;
  font-weight: 760;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.lf-admin-entry {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 10px;
  margin-top: 22px;
  padding: 12px;
  border: 1px solid rgba(200, 118, 34, 0.18);
  border-radius: var(--lf-radius-lg);
  background: var(--lf-warning-50);
  color: #73410d;
  text-decoration: none;
}

.lf-admin-entry span { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.lf-admin-entry strong { font-size: 12px; }
.lf-admin-entry small { overflow: hidden; font-size: 10px; opacity: 0.72; text-overflow: ellipsis; white-space: nowrap; }
.lf-sidebar-spacer { flex: 1; }

.lf-sidebar-account {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 11px;
  border: 1px solid var(--lf-border-subtle);
  border-radius: var(--lf-radius-lg);
  background: #ffffff;
  box-shadow: var(--lf-shadow-sm);
}

.lf-user-avatar {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 12px;
  background: var(--lf-brand-100);
  color: var(--lf-brand-800);
  font-weight: 780;
}

.lf-user-copy { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.lf-user-copy strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.lf-user-copy span { color: var(--lf-text-subtle); font-size: 10px; }

.lf-workspace-stage { display: flex; min-width: 0; min-height: 100vh; flex-direction: column; }

.lf-workspace-topbar {
  position: sticky;
  z-index: var(--lf-z-sticky);
  top: 0;
  display: flex;
  min-height: 82px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px clamp(20px, 3vw, 44px);
  border-bottom: 1px solid var(--lf-border-subtle);
  background: rgba(243, 247, 246, 0.86);
  backdrop-filter: blur(18px);
}

.lf-mobile-menu { display: none; }
.lf-page-context { min-width: 0; }

.lf-page-eyebrow {
  color: var(--lf-brand-700);
  font-size: 10px;
  font-weight: 760;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.lf-page-title-row { display: flex; align-items: center; gap: 12px; margin-top: 3px; }
.lf-page-title-row h1 { margin: 0; font-size: 20px; letter-spacing: -0.025em; }

.lf-live-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--lf-text-subtle);
  font-size: 11px;
}

.lf-live-status i { width: 7px; height: 7px; border-radius: 50%; background: var(--lf-success-600); box-shadow: 0 0 0 4px rgba(22, 131, 91, 0.1); }
.lf-topbar-actions { display: flex; align-items: center; gap: 12px; }
.lf-topbar-user { display: flex; flex-direction: column; align-items: flex-end; }
.lf-topbar-user span { font-size: 13px; font-weight: 680; }
.lf-topbar-user small { color: var(--lf-text-subtle); font-size: 10px; }

.lf-workspace-main {
  width: min(100%, var(--lf-content-lg));
  flex: 1;
  align-self: center;
  padding: clamp(20px, 3vw, 42px);
  outline: none;
}

.lf-workspace-footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px clamp(20px, 3vw, 44px) 26px;
  color: var(--lf-text-subtle);
  font-size: 11px;
}

.lf-workspace-footer a { color: inherit; text-decoration: none; }
.lf-drawer-content { display: flex; min-height: calc(100vh - 90px); flex-direction: column; gap: 24px; }

@media (max-width: 1024px) {
  .lf-workspace-shell { grid-template-columns: 1fr; }
  .lf-workspace-sidebar { display: none; }
  .lf-mobile-menu { display: inline-flex; flex: 0 0 auto; }
}

@media (max-width: 640px) {
  .lf-workspace-topbar { min-height: 70px; padding: 10px 14px; gap: 10px; }
  .lf-page-title-row h1 { font-size: 17px; }
  .lf-live-status, .lf-topbar-user { display: none; }
  .lf-workspace-main { padding: 16px 12px 28px; }
  .lf-workspace-footer { flex-direction: column; padding: 14px 16px 22px; }
}
</style>
