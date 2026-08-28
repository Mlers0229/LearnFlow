<template>
  <section class="lf-resource-panel" :aria-labelledby="headingId">
    <div class="lf-resource-panel__head">
      <div>
        <span class="lf-resource-panel__eyebrow">{{ eyebrow }}</span>
        <h3 :id="headingId">{{ title }}</h3>
        <p v-if="description">{{ description }}</p>
      </div>
      <n-button size="small" secondary :loading="loading" :disabled="disabled" @click="$emit('load')">
        {{ loadedOnce ? '刷新推荐' : actionLabel }}
      </n-button>
    </div>

    <n-alert v-if="error" type="error" :show-icon="false">{{ error }}</n-alert>
    <div v-else-if="loading" class="lf-resource-panel__loading">
      <n-skeleton text :repeat="3" />
      <n-skeleton height="84px" />
    </div>
    <n-empty v-else-if="loadedOnce && !items.length" description="暂未匹配到合适资源">
      <template #extra>
        <n-space>
          <n-button size="small" type="primary" @click="$emit('upload')">上传同主题资源</n-button>
          <n-button size="small" @click="$emit('load')">重新获取</n-button>
        </n-space>
      </template>
    </n-empty>
    <div v-else-if="items.length" class="lf-resource-grid">
      <article v-for="resource in items" :key="resource.id || resource.url" class="lf-resource-card">
        <div class="lf-resource-card__top">
          <button type="button" class="lf-resource-card__open" :disabled="!canViewResource(resource)" @click="openResource(resource)">
            <span>{{ resource.title || '未命名资源' }}</span>
            <small>{{ canViewResource(resource) ? resourceActionLabel(resource) : '暂不可查看' }}</small>
          </button>
          <n-tag v-if="resource.level" size="tiny" round>{{ resource.level }}</n-tag>
        </div>
        <div class="lf-resource-card__meta">
          <span v-if="resource.domain">{{ formatResourceDomain(resource.domain) }}</span>
          <span v-if="resource.durationMinutes">约 {{ resource.durationMinutes }} 分钟</span>
          <span v-if="resource.tags">{{ resource.tags }}</span>
        </div>
        <p v-if="resource.reason" class="lf-resource-card__reason">{{ resource.reason }}</p>
        <div v-if="buildResourceQualityParts(resource).length" class="lf-resource-card__quality">
          <n-tag v-for="part in buildResourceQualityParts(resource)" :key="part" size="tiny">{{ part }}</n-tag>
        </div>
        <ResourceEvidenceList
          :evidence="resource.evidence"
          :evidence-status="resource.evidenceStatus"
          :confidence="resource.confidence"
        />
        <div v-if="resource.id" class="lf-resource-card__feedback">
          <span>这条推荐：</span>
          <n-button text size="tiny" :disabled="feedbackState(resource.id).loading" @click="$emit('feedback', resource, 'helpful')">有帮助</n-button>
          <n-button text size="tiny" type="error" :disabled="feedbackState(resource.id).loading" @click="$emit('feedback', resource, 'invalid')">不相关</n-button>
          <n-tag v-if="feedbackState(resource.id).value" size="tiny" type="info">
            {{ formatFeedback(feedbackState(resource.id).value) }}
          </n-tag>
        </div>
      </article>
    </div>
    <div v-else class="lf-resource-panel__placeholder">
      <span>按需加载</span>
      <p>推荐结果会结合当前{{ scopeLabel }}，并保留来源与质量证据。</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import ResourceEvidenceList from '../../../components/ResourceEvidenceList.vue';
import { buildResourceQualityParts } from '../../../utils/resource';
import { formatResourceDomain } from '../utils/planHistory';
import { useResourceViewer } from '../../resources/viewer/useResourceViewer';

type Resource = Record<string, unknown> & {
  id?: number | string;
  url?: string;
  title?: string;
  level?: string;
  domain?: string;
  durationMinutes?: number;
  tags?: string;
  reason?: string;
  evidence?: unknown[];
  evidenceStatus?: string;
  confidence?: number;
  sourceType?: string;
  ingestionStatus?: string;
};
type FeedbackState = { loading: boolean; value?: string | null };

const props = withDefaults(defineProps<{
  eyebrow?: string;
  title: string;
  description?: string;
  actionLabel?: string;
  scopeLabel?: string;
  items?: Resource[];
  loading?: boolean;
  loadedOnce?: boolean;
  error?: string;
  disabled?: boolean;
  feedbackState: (id: number | string) => FeedbackState;
  formatFeedback: (value?: string | null) => string;
}>(), {
  eyebrow: '学习资源',
  actionLabel: '加载推荐',
  scopeLabel: '学习目标',
  description: '',
  items: () => [],
  loading: false,
  loadedOnce: false,
  error: '',
  disabled: false
});

defineEmits<{
  (event: 'load'): void;
  (event: 'upload'): void;
  (event: 'feedback', resource: Resource, value: 'helpful' | 'invalid'): void;
}>();

const { canViewResource, resourceActionLabel, openResource } = useResourceViewer();
const headingId = computed(() => `resource-${props.title.replace(/\s+/g, '-').toLowerCase()}`);
</script>

<style scoped>
.lf-resource-panel { display: grid; gap: 16px; padding: 20px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 20px; background: var(--lf-surface, #fff); }
.lf-resource-panel__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.lf-resource-panel__eyebrow { color: var(--lf-brand-700, #147a73); font-size: 11px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.lf-resource-panel h3 { margin: 4px 0 0; color: var(--lf-text, #17313d); font-size: 18px; }.lf-resource-panel__head p { margin: 5px 0 0; color: var(--lf-text-muted, #62737b); font-size: 12px; }
.lf-resource-panel__loading { display: grid; gap: 10px; }.lf-resource-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.lf-resource-card { display: grid; gap: 10px; min-width: 0; padding: 15px; border: 1px solid var(--lf-border, #e3e9ec); border-radius: 15px; background: var(--lf-surface-soft, #f7f9f8); }
.lf-resource-card__top { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }.lf-resource-card__open{display:grid;gap:2px;min-width:0;padding:0;border:0;background:transparent;color:var(--lf-brand-800,#0d625d);text-align:left;cursor:pointer}.lf-resource-card__open span{overflow:hidden;font-size:14px;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.lf-resource-card__open small{color:var(--lf-text-muted,#62737b);font-size:9px}.lf-resource-card__open:hover span{text-decoration:underline}.lf-resource-card__open:disabled{cursor:not-allowed;opacity:.55}
.lf-resource-card__meta, .lf-resource-card__quality, .lf-resource-card__feedback { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 10px; color: var(--lf-text-muted, #62737b); font-size: 11px; }.lf-resource-card__reason { margin: 0; color: var(--lf-text, #334b54); font-size: 12px; line-height: 1.6; }
.lf-resource-card__feedback { padding-top: 9px; border-top: 1px solid var(--lf-border, #e3e9ec); }.lf-resource-panel__placeholder { padding: 24px; color: var(--lf-text-muted, #62737b); text-align: center; border: 1px dashed var(--lf-border-strong, #cbd7da); border-radius: 14px; }.lf-resource-panel__placeholder span { color: var(--lf-text, #334b54); font-weight: 800; }.lf-resource-panel__placeholder p { margin: 5px 0 0; font-size: 12px; }
@media (max-width: 720px) { .lf-resource-grid { grid-template-columns: 1fr; }.lf-resource-panel__head { flex-direction: column; }.lf-resource-panel__head :deep(.n-button) { width: 100%; } }
</style>
