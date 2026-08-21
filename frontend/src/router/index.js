import { createRouter, createWebHistory } from 'vue-router';
import UserLayout from '../components/UserLayout.vue';
import AdminLayout from '../components/AdminLayout.vue';
import { useAuthStore } from '../store/auth';

const PlanGeneratorPage = () => import('../pages/PlanGeneratorPage.vue');
const PlanHistoryPage = () => import('../pages/PlanHistoryPage.vue');
const ExerciseReviewPage = () => import('../pages/ExerciseReviewPage.vue');
const AboutPage = () => import('../pages/AboutPage.vue');
const ResourceManagePage = () => import('../pages/ResourceManagePage.vue');
const ResourceUploadPage = () => import('../pages/ResourceUploadPage.vue');
const LoginPage = () => import('../pages/LoginPage.vue');
const RegisterPage = () => import('../pages/RegisterPage.vue');
const PasswordResetPage = () => import('../pages/PasswordResetPage.vue');
const AgentLogDebugPage = () => import('../pages/AgentLogDebugPage.vue');
const ProfilePage = () => import('../pages/ProfilePage.vue');
const AdminDashboardPage = () => import('../pages/AdminDashboardPage.vue');

const routes = [
  {
    path: '/',
    component: UserLayout,
    children: [
      {
        path: '',
        name: 'plan-generator',
        component: PlanGeneratorPage,
        meta: { requiresAuth: true }
      },
      {
        path: 'history',
        name: 'plan-history',
        component: PlanHistoryPage,
        meta: { requiresAuth: true }
      },
      {
        path: 'exercise-review',
        name: 'exercise-review',
        component: ExerciseReviewPage,
        meta: { requiresAuth: true }
      },
      {
        path: 'about',
        name: 'about',
        component: AboutPage,
        meta: { requiresAuth: true }
      },
      {
        path: 'profile',
        name: 'profile',
        component: ProfilePage,
        meta: { requiresAuth: true }
      },
      {
        path: 'chat',
        name: 'chat',
        component: () => import('../pages/ChatPage.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'debug/agent-logs',
        name: 'agent-log-debug',
        component: AgentLogDebugPage,
        meta: { requiresAuth: true }
      },
      {
        path: 'upload-resource',
        name: 'upload-resource',
        component: ResourceUploadPage,
        meta: { requiresAuth: true }
      },
      {
        path: 'login',
        name: 'login',
        component: LoginPage
      },
      {
        path: 'register',
        name: 'register',
        component: RegisterPage
      },
      {
        path: 'reset-password',
        name: 'password-reset',
        component: PasswordResetPage
      }
    ]
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'admin-dashboard',
        component: AdminDashboardPage
      },
      {
        path: 'resources',
        name: 'admin-resources',
        component: ResourceManagePage
      },
      {
        path: 'logs',
        name: 'admin-agent-logs',
        component: AgentLogDebugPage
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('../pages/AdminUserPage.vue')
      },
      {
        path: 'models',
        name: 'admin-models',
        component: () => import('../pages/AdminModelConfigPage.vue')
      }
    ]
  }
];

export const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to, from, next) => {
  const { currentUser, isLoggedIn, initialize } = useAuthStore();
  await initialize();

  // 管理端权限校验
  if (to.meta.requiresAdmin && (!currentUser.value || currentUser.value.role !== 'admin')) {
    return next('/login');
  }

  // 已登录用户访问登录 / 注册时，直接跳回首页
  if (isLoggedIn.value && (to.name === 'login' || to.name === 'register' || to.name === 'password-reset')) {
    return next({ name: 'plan-generator' });
  }

  // 普通页面登录校验
  if (to.meta.requiresAuth && !isLoggedIn.value) {
    return next({ name: 'login' });
  }

  return next();
});



