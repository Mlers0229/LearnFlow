<template>
  <Teleport to="body">
    <div v-if="open" class="overlay" @click.self="$emit('cancel')">
      <section class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="resource-confirm-title">
        <div class="eyebrow">确认管理操作</div>
        <h2 id="resource-confirm-title">{{ title }}</h2>
        <p>{{ description }}</p>
        <div class="impact"><strong>{{ count }}</strong><span>条资源将受到影响</span></div>
        <div class="actions">
          <button type="button" class="cancel" :disabled="busy" @click="$emit('cancel')">取消</button>
          <button type="button" class="confirm" :class="{ danger }" :disabled="busy" @click="$emit('confirm')">{{ busy ? '正在执行…' : confirmText }}</button>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
defineProps<{ open: boolean; title: string; description: string; count: number; confirmText: string; danger?: boolean; busy?: boolean }>();
defineEmits<{ cancel: []; confirm: [] }>();
</script>

<style scoped>
.overlay{position:fixed;inset:0;z-index:2400;display:grid;place-items:center;padding:20px;background:rgba(15,33,30,.48);backdrop-filter:blur(5px)}.dialog{width:min(430px,100%);padding:26px;border-radius:22px;background:#fff;box-shadow:0 24px 70px rgba(13,34,30,.24)}.eyebrow{font-size:11px;letter-spacing:.14em;text-transform:uppercase;color:#73837f}.dialog h2{margin:8px 0;color:#183833;font-size:22px}.dialog p{margin:0;color:#667975;line-height:1.7}.impact{display:flex;align-items:baseline;gap:8px;margin:20px 0;padding:14px;border-radius:14px;background:#f2f7f4;color:#546963}.impact strong{font-size:27px;color:#176b5a}.actions{display:flex;justify-content:flex-end;gap:9px}.actions button{border:0;border-radius:10px;padding:10px 16px;font-weight:700;cursor:pointer}.cancel{background:#edf2f0;color:#48615c}.confirm{background:#176b5a;color:#fff}.confirm.danger{background:#a94732}.actions button:disabled{opacity:.55;cursor:wait}
</style>
