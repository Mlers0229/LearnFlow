<template>
  <div v-if="normalizedEvidence.length" class="resource-evidence">
    <div class="resource-evidence-heading">
      <span>引用证据</span>
      <span v-if="formattedConfidence" class="resource-confidence">
        相关度 {{ formattedConfidence }}
      </span>
    </div>
    <ol class="resource-evidence-list">
      <li v-for="item in normalizedEvidence" :key="item.chunkId" class="resource-evidence-item">
        <a
          class="resource-evidence-link"
          :href="item.sourceUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ item.excerpt }}
        </a>
        <span v-if="item.retrievalChannels?.length" class="resource-evidence-channel">
          {{ item.retrievalChannels.join(' + ') }}
        </span>
      </li>
    </ol>
  </div>
  <div v-else-if="evidenceStatus === 'insufficient'" class="resource-evidence-warning">
    当前结果缺少达到置信度门槛的可验证证据。
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  evidence: {
    type: Array,
    default: () => []
  },
  evidenceStatus: {
    type: String,
    default: 'unverified'
  },
  confidence: {
    type: Number,
    default: null
  }
});

const normalizedEvidence = computed(() =>
  props.evidence.filter(
    (item) => item?.chunkId && item?.excerpt && /^https?:\/\//i.test(item?.sourceUrl || '')
  )
);

const formattedConfidence = computed(() => {
  if (!Number.isFinite(props.confidence)) return '';
  return `${Math.round(props.confidence * 100)}%`;
});
</script>

<style scoped>
.resource-evidence {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(37, 99, 235, 0.18);
  background: #f8fbff;
}

.resource-evidence-heading {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #1e3a5f;
}

.resource-confidence,
.resource-evidence-channel {
  font-size: 11px;
  font-weight: 500;
  color: #64748b;
}

.resource-evidence-list {
  margin: 8px 0 0;
  padding-left: 20px;
}

.resource-evidence-item {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
}

.resource-evidence-link {
  color: #1d4ed8;
  text-decoration: none;
}

.resource-evidence-link:hover {
  text-decoration: underline;
}

.resource-evidence-channel {
  margin-left: 8px;
}

.resource-evidence-warning {
  margin-top: 8px;
  font-size: 12px;
  color: #92400e;
}
</style>
