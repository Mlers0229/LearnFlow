<template>
  <Teleport to="body">
    <Transition name="session-fade">
      <div v-if="sessionExpiryState.visible" class="session-overlay" role="presentation" @click.self="closeSessionExpired">
        <section class="session-dialog" role="alertdialog" aria-modal="true" aria-labelledby="session-title">
          <div class="session-mark" aria-hidden="true">!</div>
          <div>
            <p class="session-eyebrow">SESSION EXPIRED</p>
            <h2 id="session-title">登录状态已失效</h2>
            <p>为保护账户安全，本次会话已结束。重新登录后会返回当前页面。</p>
          </div>
          <div class="session-actions">
            <button class="session-secondary" type="button" @click="closeSessionExpired">稍后处理</button>
            <button class="session-primary" type="button" @click="signInAgain">重新登录</button>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import {
  closeSessionExpired,
  createLoginUrl,
  sessionExpiryState,
} from '../shared/session/sessionExpiry'

function signInAgain() {
  window.location.assign(createLoginUrl())
}
</script>

<style scoped>
.session-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(9, 17, 31, 0.58);
  backdrop-filter: blur(8px);
}

.session-dialog {
  width: min(460px, 100%);
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 18px;
  padding: 28px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 24px 80px rgba(9, 17, 31, 0.28);
}

.session-mark {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 15px;
  color: #fff;
  background: #e26a3f;
  font-size: 24px;
  font-weight: 800;
}

.session-eyebrow {
  margin: 1px 0 7px;
  color: #e26a3f;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

h2 { margin: 0; color: #172338; font-size: 22px; }
p { margin: 9px 0 0; color: #667085; line-height: 1.65; }

.session-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

button {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 12px;
  border: 0;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.session-secondary { color: #526077; background: #eef1f6; }
.session-primary { color: #fff; background: #172338; }
.session-fade-enter-active, .session-fade-leave-active { transition: opacity 160ms ease; }
.session-fade-enter-from, .session-fade-leave-to { opacity: 0; }

@media (max-width: 520px) {
  .session-dialog { grid-template-columns: 1fr; padding: 22px; }
  .session-actions { grid-column: auto; }
  .session-actions button { flex: 1; }
}
</style>
