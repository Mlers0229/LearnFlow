<template>
  <div>
    <header class="app-header">
      <div class="app-header-inner">
        <div class="logo">
          <span class="logo-dot" />
          <span class="logo-text">LearnFlow</span>
        </div>
        <nav class="nav">
          <RouterLink
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="nav-link"
            active-class="nav-link-active"
          >
            {{ item.label }}
          </RouterLink>
        </nav>
      </div>
    </header>

    <main>
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { RouterLink, RouterView } from 'vue-router';

const navItems = [
  { to: '/', label: '生成学习计划' },
  { to: '/history', label: '历史计划' },
  { to: '/resources', label: '资源管理' },
  { to: '/about', label: '关于系统' }
];
</script>


