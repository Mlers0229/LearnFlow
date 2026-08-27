<script setup lang="ts">
import { computed } from 'vue'
import { useUiStore } from '../../store/ui'
import type { ProfileFeedback, ProfileForm } from './types'

defineProps<{ form: ProfileForm; dirty: boolean; feedback: ProfileFeedback }>()
const emit = defineEmits<{ level: [value: string]; save: []; reset: [] }>()
const { theme, fontScale, toggleTheme, increaseFont, decreaseFont } = useUiStore()
const fontPercent = computed(() => Math.round(fontScale.value * 100))
const levels = [
  { value: 'beginner', title: '零基础', detail: '从核心概念和引导练习开始' },
  { value: 'intermediate', title: '有一点基础', detail: '平衡原理说明与动手实践' },
  { value: 'advanced', title: '进阶', detail: '减少基础解释，聚焦复杂任务' },
]
</script>

<template>
  <section class="settings-panel" aria-labelledby="preferences-title">
    <div class="panel-heading"><div><span>PREFERENCES</span><h2 id="preferences-title">学习偏好</h2><p>调整计划生成的起点，以及当前设备上的阅读体验。</p></div><span v-if="dirty" class="dirty-badge">水平待保存</span></div>
    <fieldset class="level-grid">
      <legend>当前学习水平</legend>
      <button v-for="item in levels" :key="item.value" type="button" :class="{ active: form.level === item.value }" @click="emit('level', item.value)">
        <span>{{ form.level === item.value ? '✓' : '○' }}</span><div><strong>{{ item.title }}</strong><small>{{ item.detail }}</small></div>
      </button>
    </fieldset>
    <div class="display-card">
      <div><span>本机显示</span><h3>主题与字号</h3><p>这些设置立即生效，并只保存在当前浏览器。</p></div>
      <div class="display-controls">
        <button type="button" data-testid="theme-toggle" @click="toggleTheme">{{ theme === 'dark' ? '切换亮色' : '切换暗色' }}</button>
        <div class="font-control"><button type="button" aria-label="减小字号" @click="decreaseFont">A−</button><strong>{{ fontPercent }}%</strong><button type="button" aria-label="增大字号" @click="increaseFont">A＋</button></div>
      </div>
    </div>
    <div v-if="feedback.section === 'preferences' && feedback.state !== 'idle'" class="feedback" :class="feedback.state" aria-live="polite">{{ feedback.message }}</div>
    <div class="panel-actions"><button type="button" class="secondary" :disabled="!dirty || feedback.state === 'saving'" @click="emit('reset')">撤销</button><button type="button" class="primary" :disabled="!dirty || feedback.state === 'saving'" data-testid="save-preferences" @click="emit('save')">{{ feedback.section === 'preferences' && feedback.state === 'saving' ? '保存中…' : '保存学习偏好' }}</button></div>
  </section>
</template>

<style scoped>
.settings-panel{display:grid;gap:24px}.panel-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:18px}.panel-heading>div>span,.display-card>div>span{font-size:11px;font-weight:850;letter-spacing:.15em;color:#1a7967}.panel-heading h2{margin:7px 0 6px;font-size:30px;letter-spacing:-.04em;color:#193d36}.panel-heading p,.display-card p{margin:0;color:#6c817c;line-height:1.6}.dirty-badge{padding:6px 10px;border-radius:999px;background:#fff2d8;color:#855e12;font-size:11px;font-weight:800;white-space:nowrap}.level-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;border:0;margin:0;padding:0}.level-grid legend{grid-column:1/-1;margin-bottom:3px;font-weight:750;color:#294b44}.level-grid button{display:flex;gap:10px;text-align:left;border:1px solid #d9e6e2;border-radius:15px;padding:15px;background:#fff;color:#2c4e47;cursor:pointer}.level-grid button.active{border-color:#1c7a67;background:#eef8f5;box-shadow:0 0 0 3px rgba(28,122,103,.07)}.level-grid button>span{color:#1b7967;font-weight:800}.level-grid button div{display:grid;gap:5px}.level-grid small{color:#72857f;line-height:1.45}.display-card{display:flex;justify-content:space-between;align-items:center;gap:20px;padding:19px;border-radius:17px;background:#f4f8f6;border:1px solid #e1eae7}.display-card h3{margin:5px 0 3px;color:#294b44}.display-controls{display:flex;gap:9px;align-items:center}.display-controls button{border:1px solid #c9dad5;border-radius:10px;background:#fff;color:#315b51;padding:9px 11px;font-weight:750;cursor:pointer}.font-control{display:flex;align-items:center;border:1px solid #cadbd6;border-radius:10px;background:#fff;overflow:hidden}.font-control button{border:0;border-radius:0}.font-control strong{min-width:48px;text-align:center;font-size:12px}.feedback{padding:12px 14px;border-radius:12px;font-size:13px}.feedback.success{background:#ebf8f2;color:#17634e}.feedback.error{background:#fff0ed;color:#963f31}.feedback.saving{background:#eef5f3;color:#3b665d}.panel-actions{display:flex;justify-content:flex-end;gap:10px;padding-top:18px;border-top:1px solid #e5ece9}.panel-actions button{border-radius:11px;padding:11px 16px;font:inherit;font-weight:800;cursor:pointer}.panel-actions button:disabled{opacity:.45;cursor:not-allowed}.primary{border:0;background:#1b7765;color:#fff}.secondary{border:1px solid #cadbd6;background:#fff;color:#42635c}@media(max-width:760px){.level-grid{grid-template-columns:1fr}.display-card{align-items:flex-start;flex-direction:column}}@media(max-width:620px){.panel-heading{flex-direction:column}.display-controls{width:100%;flex-wrap:wrap}.panel-actions button{flex:1}}
</style>
