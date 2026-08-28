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
const AdminAgentLogsPage = () => import('../pages/AdminAgentLogsPage.vue');
const AdminUsersPage = () => import('../pages/AdminUsersPage.vue');
const ProfilePage = () => import('../pages/ProfilePage.vue');
const AdminDashboardPage = () => import('../pages/AdminDashboardPage.vue');
const SystemStatePage = () => import('../pages/SystemStatePage.vue');

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
        component: AdminAgentLogsPage,
        meta: { requiresAdmin: true }
      },
      {
        path: 'upload-resource',
        name: 'upload-resource',
        component: ResourceUploadPage,
        meta: { requiresAuth: true }
      }
    ]
  },
  {
    path: '/login',
    name: 'login',
    component: LoginPage
  },
  {
    path: '/register',
    name: 'register',
    component: RegisterPage
  },
  {
    path: '/reset-password',
    name: 'password-reset',
    component: PasswordResetPage
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
        component: AdminAgentLogsPage
      },
      {
        path: 'users',
        name: 'admin-users',
        component: AdminUsersPage
      },
      {
        path: 'models',
        name: 'admin-models',
        component: () => import('../pages/AdminModelConfigPage.vue')
      }
    ]
  },
  {
    path: '/forbidden',
    name: 'forbidden',
    component: SystemStatePage,
    props: {
      code: '403',
      eyebrow: 'Access restricted',
      title: '当前账号没有访问权限',
      description: '这个页面仅向具备相应权限的账号开放。你可以返回学习工作台，或切换到管理员账号后重试。'
    }
  },
  {
    path: '/error',
    name: 'system-error',
    component: SystemStatePage,
    props: {
      code: '500',
      eyebrow: 'Something went wrong',
      title: 'LearnFlow 暂时无法完成请求',
      description: '你的学习数据不会因此丢失。请稍后重试；如果问题持续存在，可以记录发生时间并联系管理员。',
      showRetry: true
    }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: SystemStatePage,
    props: {
      code: '404',
      eyebrow: 'Page not found',
      title: '没有找到这个页面',
      description: '链接可能已经失效，或者页面地址发生了变化。请返回学习工作台继续使用。'
    }
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
  if (to.meta.requiresAdmin && !isLoggedIn.value) {
    return next({ name: 'login', query: { redirect: to.fullPath } });
  }

  if (to.meta.requiresAdmin && currentUser.value?.role !== 'admin') {
    return next({ name: 'forbidden' });
  }

  // 已登录用户访问登录 / 注册时，直接跳回首页
  if (isLoggedIn.value && (to.name === 'login' || to.name === 'register' || to.name === 'password-reset')) {
    return next({ name: 'plan-generator' });
  }

  // 普通页面登录校验
  if (to.meta.requiresAuth && !isLoggedIn.value) {
    return next({ name: 'login', query: { redirect: to.fullPath } });
  }

  return next();
});



