<template>
  <main class="lf-auth-page">
    <div class="lf-auth-topbar">
      <AppBrand to="/login" />
      <slot name="top-action" />
    </div>

    <div class="lf-auth-shell">
      <section class="lf-auth-story" aria-labelledby="auth-story-title">
        <div class="lf-auth-story-glow" aria-hidden="true" />
        <div class="lf-auth-story-content">
          <div class="lf-auth-kicker">{{ eyebrow }}</div>
          <h1 id="auth-story-title">{{ title }}</h1>
          <p>{{ description }}</p>

          <div class="lf-auth-highlights" aria-label="LearnFlow 核心能力">
            <span v-for="item in highlights" :key="item">
              <Check :size="14" aria-hidden="true" />
              {{ item }}
            </span>
          </div>

          <div class="lf-auth-journey">
            <div class="lf-auth-journey-label">你的学习闭环</div>
            <div class="lf-auth-journey-steps">
              <span>设定目标</span><ArrowRight :size="14" /><span>执行计划</span><ArrowRight :size="14" /><span>练习复盘</span>
            </div>
          </div>
        </div>
      </section>

      <section class="lf-auth-form-panel">
        <div class="lf-auth-form-card">
          <slot />
        </div>
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer" class="lf-auth-record">
          津ICP备2024026404号-2
        </a>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ArrowRight, Check } from 'lucide-vue-next';
import AppBrand from '../../../shared/components/AppBrand.vue';

withDefaults(
  defineProps<{
    eyebrow: string;
    title: string;
    description: string;
    highlights?: string[];
  }>(),
  {
    highlights: () => []
  }
);
</script>

<style scoped>
.lf-auth-page {
  min-height: 100vh;
  padding: 22px clamp(16px, 3vw, 44px) 28px;
  background:
    radial-gradient(circle at 8% 8%, rgba(72, 175, 168, 0.15), transparent 28%),
    radial-gradient(circle at 92% 88%, rgba(244, 186, 119, 0.12), transparent 24%),
    var(--lf-bg-canvas);
}

.lf-auth-topbar {
  display: flex;
  width: min(1240px, 100%);
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  margin: 0 auto 22px;
}

.lf-auth-shell {
  display: grid;
  width: min(1240px, 100%);
  min-height: calc(100vh - 120px);
  grid-template-columns: minmax(0, 1.05fr) minmax(380px, 0.72fr);
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid var(--lf-border-subtle);
  border-radius: 32px;
  background: var(--lf-surface-raised);
  box-shadow: var(--lf-shadow-xl);
}

.lf-auth-story {
  position: relative;
  display: flex;
  overflow: hidden;
  align-items: flex-end;
  padding: clamp(34px, 6vw, 72px);
  background:
    linear-gradient(155deg, rgba(8, 39, 47, 0.92), rgba(15, 75, 78, 0.92)),
    var(--lf-brand-900);
  color: #ffffff;
}

.lf-auth-story::after {
  position: absolute;
  right: -12%;
  bottom: -16%;
  width: 62%;
  height: 62%;
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 50%;
  box-shadow: 0 0 0 42px rgba(255, 255, 255, 0.025), 0 0 0 84px rgba(255, 255, 255, 0.018);
  content: '';
}

.lf-auth-story-glow {
  position: absolute;
  top: -100px;
  left: -80px;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: rgba(72, 175, 168, 0.22);
  filter: blur(20px);
}

.lf-auth-story-content {
  position: relative;
  z-index: 1;
  max-width: 640px;
}

.lf-auth-kicker {
  color: #9fe0d9;
  font-size: 12px;
  font-weight: 740;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.lf-auth-story h1 {
  max-width: 12ch;
  margin: 14px 0 16px;
  font-size: clamp(38px, 5.5vw, 66px);
  letter-spacing: -0.055em;
  line-height: 1.02;
}

.lf-auth-story p {
  max-width: 58ch;
  margin: 0;
  color: rgba(235, 247, 247, 0.79);
  font-size: 15px;
  line-height: 1.85;
}

.lf-auth-highlights {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: 26px;
}

.lf-auth-highlights span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 11px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--lf-radius-pill);
  background: rgba(255, 255, 255, 0.07);
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
}

.lf-auth-journey {
  margin-top: 38px;
  padding-top: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
}

.lf-auth-journey-label {
  color: rgba(226, 241, 242, 0.62);
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.lf-auth-journey-steps {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 10px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 13px;
  font-weight: 650;
}

.lf-auth-form-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  padding: clamp(28px, 4.5vw, 58px);
}

.lf-auth-form-card {
  width: min(100%, 440px);
  margin: auto;
}

.lf-auth-record {
  align-self: center;
  margin-top: 28px;
  color: var(--lf-text-subtle);
  font-size: 12px;
  text-decoration: none;
}

@media (max-width: 900px) {
  .lf-auth-shell {
    grid-template-columns: 1fr;
  }

  .lf-auth-story {
    min-height: 360px;
    padding: 36px;
  }

  .lf-auth-story h1 {
    max-width: 16ch;
    font-size: clamp(34px, 8vw, 50px);
  }
}

@media (max-width: 560px) {
  .lf-auth-page {
    padding: 14px 10px 18px;
  }

  .lf-auth-topbar {
    margin-bottom: 14px;
    padding: 0 6px;
  }

  .lf-auth-shell {
    border-radius: 24px;
  }

  .lf-auth-story {
    min-height: 310px;
    padding: 28px 22px;
  }

  .lf-auth-journey {
    display: none;
  }

  .lf-auth-form-panel {
    padding: 30px 22px;
  }
}
</style>
