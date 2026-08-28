<template>
  <Teleport to="body">
    <Transition name="replan-fade">
      <div v-if="open" class="replan-overlay" @click.self="emit('close')">
        <section class="replan-dialog" role="dialog" aria-modal="true" aria-labelledby="replan-title">
          <header>
            <p>ADAPTIVE PLANNING</p>
            <h2 id="replan-title">顺延并重排学习安排</h2>
            <span>{{ dayTitle || '当前学习日' }}之后的安排将根据新节奏重新生成。</span>
          </header>

          <label>
            <span>顺延天数</span>
            <select v-model.number="delayDays" :disabled="busy">
              <option :value="1">1 天</option>
              <option :value="2">2 天</option>
              <option :value="3">3 天</option>
              <option :value="7">1 周</option>
            </select>
          </label>

          <label>
            <span>调整原因（可选）</span>
            <textarea
              v-model.trim="reason"
              rows="4"
              maxlength="300"
              :disabled="busy"
              placeholder="例如：本周工作安排有变化，希望顺延后续学习任务"
            />
            <small>{{ reason.length }}/300</small>
          </label>

          <p v-if="error" class="replan-error" role="alert">{{ error }}</p>

          <footer>
            <button class="replan-secondary" type="button" :disabled="busy" @click="emit('close')">取消</button>
            <button class="replan-primary" type="button" :disabled="busy" @click="submit">
              {{ busy ? '正在重排…' : '确认重排' }}
            </button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  open: boolean
  dayTitle?: string
  busy?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: { delayDays: number; reason: string }]
}>()

const delayDays = ref(1)
const reason = ref('')

watch(() => props.open, (open) => {
  if (open) {
    delayDays.value = 1
    reason.value = ''
  }
})

function submit() {
  emit('submit', { delayDays: delayDays.value, reason: reason.value })
}
</script>

<style scoped>
.replan-overlay { position: fixed; inset: 0; z-index: 9999; display: grid; place-items: center; padding: 24px; background: rgba(9, 17, 31, .58); backdrop-filter: blur(8px); }
.replan-dialog { width: min(520px, 100%); padding: 28px; border: 1px solid rgba(255,255,255,.72); border-radius: 24px; background: rgba(255,255,255,.97); box-shadow: 0 24px 80px rgba(9,17,31,.28); }
header p { margin: 0 0 7px; color: #7c65d9; font-size: 11px; font-weight: 800; letter-spacing: .14em; }
header h2 { margin: 0; color: #172338; font-size: 23px; }
header span { display: block; margin-top: 9px; color: #667085; line-height: 1.6; }
label { display: grid; gap: 8px; margin-top: 20px; color: #344054; font-weight: 700; }
select, textarea { width: 100%; box-sizing: border-box; border: 1px solid #d8deea; border-radius: 12px; padding: 11px 13px; color: #172338; background: #fff; font: inherit; outline: none; }
select:focus, textarea:focus { border-color: #8b78dd; box-shadow: 0 0 0 3px rgba(124,101,217,.12); }
textarea { resize: vertical; min-height: 104px; }
small { justify-self: end; color: #98a2b3; font-weight: 500; }
.replan-error { margin: 14px 0 0; padding: 10px 12px; border-radius: 10px; color: #b42318; background: #fff0ed; }
footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 22px; }
button { min-height: 41px; padding: 0 17px; border: 0; border-radius: 12px; font: inherit; font-weight: 750; cursor: pointer; }
button:disabled { cursor: wait; opacity: .62; }
.replan-secondary { color: #526077; background: #eef1f6; }
.replan-primary { color: #fff; background: #172338; }
.replan-fade-enter-active, .replan-fade-leave-active { transition: opacity 160ms ease; }
.replan-fade-enter-from, .replan-fade-leave-to { opacity: 0; }
@media (max-width: 520px) { .replan-dialog { padding: 22px; } footer button { flex: 1; } }
</style>
