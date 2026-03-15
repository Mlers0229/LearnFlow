<template>
  <div class="plan-history-page">
    <div class="history-header history-hero">
      <div>
        <div class="history-kicker">计划复盘台</div>
        <h1 class="title history-title">历史学习计划</h1>
        <p class="subtitle history-subtitle">
          左侧快速切换计划与日程，右侧集中查看资源、练习与每日执行细节，让回顾页真正具备“复盘工作台”的感觉。
        </p>
      </div>

      <div class="history-focus-card">
        <div class="history-focus-label">当前聚焦计划</div>
        <div class="history-focus-title">
          {{ currentPlan?.title || '先从左侧选择一份计划' }}
        </div>
        <div class="history-focus-meta">
          {{ currentPlan ? `共 ${totalDays} 天，已完成 ${completedDays} 天，完成度 ${completionRate}%` : '支持快速切换计划、查看每日任务与练习反馈。' }}
        </div>
      </div>
    </div>

    <n-space size="small" class="history-stats">
      <n-card size="small" :bordered="false" class="stat-card">
        <div class="stat-label">计划总天数</div>
        <div class="stat-value">{{ totalDays }}</div>
        <div class="stat-desc">
          <span v-if="currentPlan">
            覆盖 {{ currentPlan.startDate }} ~ {{ currentPlan.endDate }}
          </span>
          <span v-else>暂无计划数据</span>
        </div>
      </n-card>
      <n-card size="small" :bordered="false" class="stat-card">
        <div class="stat-label">已完成天数</div>
        <div class="stat-value">{{ completedDays }}</div>
        <div class="stat-desc">
          剩余 {{ Math.max(totalDays - completedDays, 0) }} 天待学习
        </div>
      </n-card>
      <n-card size="small" :bordered="false" class="stat-card">
        <div class="stat-label">完成度</div>
        <div class="stat-value">{{ completionRate }}%</div>
        <n-progress
          type="line"
          :percentage="completionRate"
          :height="8"
          :show-indicator="false"
        />
      </n-card>
    </n-space>

    <div class="history-main-row">
      <div class="left-column">
        <n-card size="small" class="block-card history-sidebar-card">
          <template #header>
            <div class="panel-header">
              <div>
                <div class="panel-kicker">复盘侧栏</div>
                <div class="panel-title">一级计划 / 二级日程导航</div>
              </div>
              <span class="list-subtitle">切计划时记住上次查看到的日程位置</span>
            </div>
          </template>

          <div class="sidebar-current-plan">
            <div class="sidebar-current-label">当前聚焦</div>
            <div class="sidebar-current-title">
              {{ currentPlan?.title || '请选择一份学习计划' }}
            </div>
            <div class="sidebar-current-meta">
              <template v-if="currentPlan && currentDay">
                已定位到 {{ currentDay.date }} · {{ currentDay.title }}
              </template>
              <template v-else-if="currentPlan">
                共 {{ totalDays }} 天，等待选择具体日程
              </template>
              <template v-else>
                切换计划后会自动回到你上次查看的那一天
              </template>
            </div>
          </div>

          <div v-if="listLoading" class="helper-text">正在加载历史计划...</div>
          <p v-else-if="listError" class="error-text">{{ listError }}</p>
          <p v-else-if="plans.length === 0" class="helper-text">
            暂时还没有生成过任何学习计划，可以先到“生成学习计划”页试一试。
          </p>

          <div v-else class="sidebar-stack">
            <section class="sidebar-section">
              <div class="sidebar-section-head">
                <div>
                  <div class="sidebar-section-kicker">一级导航</div>
                  <div class="sidebar-section-title">计划切换</div>
                </div>
                <span class="sidebar-section-note">共 {{ plans.length }} 条</span>
              </div>

              <div class="plan-switch-bar">
                <n-select
                  :value="selectedPlanId"
                  :options="planSelectOptions"
                  placeholder="快速切换到某个计划"
                  @update:value="handleQuickSelectPlan"
                />
                <n-button
                  size="small"
                  quaternary
                  @click="showAllPlans = !showAllPlans"
                >
                  {{ showAllPlans ? '收起计划列表' : `展开全部 ${plans.length} 条计划` }}
                </n-button>
              </div>

              <div v-if="collapsedPlanHint" class="collapsed-hint">
                {{ collapsedPlanHint }}
              </div>

              <div class="plan-list-shell" :class="{ 'plan-list-shell-expanded': showAllPlans }">
                <div
                  v-for="item in visiblePlans"
                  :key="item.id"
                  class="plan-item"
                  :class="{ 'plan-item-active': currentPlan && String(currentPlan.planId || currentPlan.plan_id || currentPlan.id) === String(item.id) }"
                  @click="onSelectPlan(item.id)"
                >
                  <div class="plan-item-header">
                    <div class="plan-item-title">
                      {{ item.title || `学习计划 #${item.id}` }}
                    </div>
                    <n-tag size="tiny" :type="statusTagType(item.status)">
                      {{ statusText(item.status) }}
                    </n-tag>
                  </div>
                  <div class="plan-item-meta">
                    <span class="mono">
                      ID：{{ item.id }}
                    </span>
                    <span class="mono">
                      {{ formatDate(item.startDate) }} ~ {{ formatDate(item.endDate) }}
                    </span>
                  </div>
                </div>
              </div>
            </section>

            <section class="sidebar-section sidebar-section-days">
              <div class="sidebar-section-head">
                <div>
                  <div class="sidebar-section-kicker">二级导航</div>
                  <div class="sidebar-section-title">当前计划日程</div>
                </div>
                <span class="sidebar-section-note">{{ totalDays }} 天</span>
              </div>

              <div v-if="currentPlan">
                <div class="day-index-summary">
                  <span>当前计划：{{ currentPlan?.title || '未选择' }}</span>
                  <span>共 {{ totalDays }} 天</span>
                </div>
                <div class="day-brief-list">
                  <div
                    v-for="day in currentPlan.days"
                    :key="day.id"
                    class="day-brief"
                    :class="{ 'day-brief-active': currentDay && currentDay.id === day.id }"
                    @click="onSelectDay(day.id)"
                  >
                    <div class="day-brief-date mono">{{ day.date }}</div>
                    <div class="day-brief-main">
                      <div class="day-brief-title">{{ day.title }}</div>
                      <n-tag size="tiny" :type="statusTagType(day.status)">
                        {{ statusText(day.status) }}
                      </n-tag>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="helper-text">请先在上方选择一个学习计划。</div>
            </section>
          </div>
        </n-card>
      </div>

      <div class="right-column">
        <div class="right-workbench">
          <div class="workbench-sticky-bar">
            <div class="workbench-sticky-copy">
              <div class="workbench-sticky-title">
                {{ currentPlan?.title || '未选择学习计划' }}
              </div>
              <div class="workbench-sticky-meta">
                <template v-if="currentDay">
                  <span>{{ currentDay.title }}</span>
                </template>
                <template v-else-if="currentPlan">
                  <span>当前计划共 {{ totalDays }} 天，等待选择具体日程</span>
                </template>
                <template v-else>
                  <span>请先从左侧选择一个计划与日程</span>
                </template>
              </div>
            </div>
            <div class="workbench-sticky-stats">
              <div class="workbench-sticky-chip">
                <span class="workbench-sticky-chip-label">完成度</span>
                <strong>{{ completionRate }}%</strong>
              </div>
              <div class="workbench-sticky-chip">
                <span class="workbench-sticky-chip-label">当前日期</span>
                <strong>{{ currentDay?.date || '未定位' }}</strong>
              </div>
            </div>
          </div>

          <!-- 基于整份计划（学习目标）推荐的全局资源卡片 -->
          <n-card size="small" class="block-card workbench-card workbench-card-summary">
            <template #header>
              <div class="detail-header">
                <div class="panel-kicker">资源总览</div>
                <div class="detail-title">当前计划推荐资源总览</div>
              </div>
            </template>

            <p v-if="!currentPlan" class="helper-text">
              请选择左侧的某个学习计划后，再为整份计划加载推荐资源。
            </p>

            <div v-else class="plan-resources-block">
              <div class="section-header">
                <div class="section-title">推荐给这份计划的核心学习资源</div>
                <n-button
                  size="tiny"
                  quaternary
                  type="primary"
                  :loading="planResources.loading"
                  @click="loadPlanResourcesForCurrentPlan"
                >
                  {{
                    planResources.loading
                      ? '正在为本计划加载推荐资源…'
                      : '为本计划加载推荐资源'
                  }}
                </n-button>
              </div>

              <p v-if="planResources.error" class="error-text">
                {{ planResources.error }}
              </p>
              <div
                v-else-if="
                  planResources.loadedOnce &&
                  !planResources.loading &&
                  planResources.items &&
                  planResources.items.length === 0
                "
                class="resource-empty-state"
              >
                <div class="resource-empty-icon">+</div>
                <div class="resource-empty-title">当前还没有匹配资源</div>
                <div class="resource-empty-desc">
                  这个学习领域暂时没有可推荐内容。补充一条同主题资源后，再回来获取推荐会更准确。
                </div>
                <div class="resource-empty-actions">
                  <n-button size="small" type="primary" @click="goToResourceUpload">
                    去上传资源
                  </n-button>
                  <n-button size="small" secondary @click="loadPlanResourcesForCurrentPlan">
                    重新获取推荐
                  </n-button>
                </div>
              </div>

              <div
                v-if="planResources.items && planResources.items.length"
                class="resources-grid"
              >
                <n-card
                  v-for="(res, rIdx) in planResources.items"
                  :key="rIdx"
                  size="small"
                  class="resource-card"
                >
                  <div class="resource-title-row">
                    <a
                      :href="res.url"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="resource-title"
                    >
                      {{ res.title }}
                    </a>
                  </div>
                  <div class="resource-meta">
                    <span v-if="res.domain">领域：{{ formatResourceDomain(res.domain) }}</span>
                    <span v-if="res.level">适合水平：{{ res.level }}</span>
                    <span v-if="res.durationMinutes">
                      预计 {{ res.durationMinutes }} 分钟
                    </span>
                    <span v-if="res.tags">标签：{{ res.tags }}</span>
                  </div>
                  <div v-if="resourceQualityParts(res).length" class="resource-quality-row">
                    <n-tag
                      v-for="part in resourceQualityParts(res)"
                      :key="part"
                      size="tiny"
                      type="default"
                    >
                      {{ part }}
                    </n-tag>
                  </div>
                  <div v-if="res.reason" class="resource-reason">
                    推荐理由：{{ res.reason }}
                  </div>
                  <div
                    v-if="res.id"
                    class="resource-feedback-row"
                  >
                    <span class="resource-feedback-label">你的感觉：</span>
                    <n-button
                      size="tiny"
                      text
                      :disabled="getFeedbackState(res.id).loading"
                      @click="handleResourceFeedback(res, 'helpful')"
                    >
                      👍 有帮助
                    </n-button>
                    <n-button
                      size="tiny"
                      text
                      type="error"
                      :disabled="getFeedbackState(res.id).loading"
                      @click="handleResourceFeedback(res, 'invalid')"
                    >
                      👎 不相关 / 无效
                    </n-button>
                    <n-tag v-if="getFeedbackState(res.id).value" size="small" type="info">
                      已反馈：{{ formatResourceFeedbackLabel(getFeedbackState(res.id).value) }}
                    </n-tag>
                  </div>
                </n-card>
              </div>
            </div>
          </n-card>

          <!-- 当前选中日的详细任务与资源 / 练习 -->
          <n-card size="small" class="block-card detail-card workbench-card workbench-card-detail">
            <template #header>
              <div class="detail-header">
                <div class="panel-kicker">执行面板</div>
                <div class="detail-title">当日日程与执行操作</div>
                <div v-if="currentPlan" class="detail-actions">
                  <n-space size="small">
                    <n-button size="tiny" quaternary @click="renameCurrentPlan">
                      重命名计划
                    </n-button>
                    <n-button
                      size="tiny"
                      quaternary
                      type="error"
                      @click="deleteCurrentPlan"
                    >
                      删除计划
                    </n-button>
                  </n-space>
                </div>
              </div>
            </template>

            <div v-if="detailLoading" class="helper-text">正在加载计划详情...</div>
            <p v-else-if="detailError" class="error-text">{{ detailError }}</p>
            <p v-else-if="!currentPlan" class="helper-text">
              请选择左侧列表中的某个计划，或先前往“生成学习计划”页创建一个新计划。
            </p>

            <div v-else-if="currentDay" class="day-detail">
            <div class="day-detail-header">
              <div>
                <div class="day-detail-title">{{ currentDay.title }}</div>
              </div>
              <div class="day-detail-actions">
                <n-tag size="small" :type="statusTagType(currentDay.status)">
                  {{ statusText(currentDay.status) }}
                </n-tag>
                <n-space size="small">
                  <n-button
                    v-if="currentDay.id && currentDay.status !== 'completed'"
                    size="tiny"
                    type="primary"
                    :loading="dayStatusSavingMap[currentDay.id]?.saving"
                    @click="markDayCompleted(currentDay)"
                  >
                    {{
                      dayStatusSavingMap[currentDay.id]?.saving
                        ? '正在标记…'
                        : '标记已完成'
                    }}
                  </n-button>
                  <n-button
                    v-if="currentDay.id"
                    size="tiny"
                    tertiary
                    :loading="dayRefineMap[currentDay.id]?.loading"
                    @click="refineDayTasks(currentDay)"
                  >
                    {{
                      dayRefineMap[currentDay.id]?.loading
                        ? '正在细化…'
                        : '细化任务'
                    }}
                  </n-button>
                  <n-button
                    v-if="currentDay.id && currentDay.status !== 'completed'"
                    size="tiny"
                    quaternary
                    type="warning"
                    :loading="dayReplanMap[currentDay.id]?.loading"
                    @click="replanCurrentDay"
                  >
                    {{
                      dayReplanMap[currentDay.id]?.loading
                        ? '正在顺延…'
                        : '顺延并重排'
                    }}
                  </n-button>
                </n-space>
              </div>
            </div>

            <p
              v-if="
                currentDay.id && dayStatusSavingMap[currentDay.id]?.error
              "
              class="error-text"
            >
              {{ dayStatusSavingMap[currentDay.id].error }}
            </p>
            <p
              v-if="
                currentDay.id && dayRefineMap[currentDay.id]?.error
              "
              class="error-text"
            >
              {{ dayRefineMap[currentDay.id].error }}
            </p>
            <p
              v-if="
                currentDay.id && dayReplanMap[currentDay.id]?.error
              "
              class="error-text"
            >
              {{ dayReplanMap[currentDay.id].error }}
            </p>

            <div class="day-detail-body">
              <div class="sub-section">
                <div class="sub-section-title">学习任务列表</div>
                <ul class="task-list">
                  <li v-for="(task, idx) in currentDay.tasks" :key="idx">
                    {{ task }}
                  </li>
                </ul>
              </div>

              <div class="sub-section">
                <div class="sub-section-title">资源与练习</div>
                <n-space size="small" wrap>
                  <n-button
                    v-if="currentDay.id"
                    size="tiny"
                    quaternary
                    type="primary"
                    :loading="dayResourcesMap[currentDay.id]?.loading"
                    @click="loadDayResources(currentDay)"
                  >
                    {{
                      dayResourcesMap[currentDay.id]?.loading
                        ? '正在为当日加载推荐资源…'
                        : '查看当日推荐资源'
                    }}
                  </n-button>
                  <n-button
                    v-if="currentDay.id"
                    size="tiny"
                    quaternary
                    :loading="dayExercisesMap[currentDay.id]?.loading"
                    @click="loadDayExercises(currentDay)"
                  >
                    {{
                      dayExercisesMap[currentDay.id]?.loading
                        ? '正在为当日生成练习题…'
                        : '生成 / 查看当日练习题'
                    }}
                  </n-button>
                </n-space>

                <p
                  v-if="
                    currentDay.id && dayResourcesMap[currentDay.id]?.error
                  "
                  class="error-text"
                >
                  {{ dayResourcesMap[currentDay.id].error }}
                </p>

                <div
                  v-else-if="
                    currentDay.id &&
                    dayResourcesMap[currentDay.id]?.loadedOnce &&
                    !dayResourcesMap[currentDay.id]?.loading &&
                    dayResourcesMap[currentDay.id]?.items &&
                    dayResourcesMap[currentDay.id].items.length === 0
                  "
                  class="resource-empty-state resource-empty-state-compact"
                >
                  <div class="resource-empty-icon">+</div>
                  <div class="resource-empty-title">当天资源暂未匹配到</div>
                  <div class="resource-empty-desc">
                    可以先补充同领域资源，再重新获取当天推荐。
                  </div>
                  <div class="resource-empty-actions">
                    <n-button size="small" type="primary" @click="goToResourceUpload">
                      去上传资源
                    </n-button>
                    <n-button size="small" secondary @click="loadDayResources(currentDay)">
                      重新获取推荐
                    </n-button>
                  </div>
                </div>

                <ul
                  v-if="
                    currentDay.id &&
                    dayResourcesMap[currentDay.id]?.items &&
                    dayResourcesMap[currentDay.id].items.length
                  "
                  class="resources-list"
                >
                  <li
                    v-for="(res, rIdx) in dayResourcesMap[currentDay.id].items"
                    :key="rIdx"
                    class="resource-item"
                  >
                    <a
                      :href="res.url"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="resource-title"
                    >
                      {{ res.title }}
                    </a>
                    <div class="resource-meta">
                      <span v-if="res.domain">领域：{{ formatResourceDomain(res.domain) }}</span>
                      <span v-if="res.level">适合水平：{{ res.level }}</span>
                      <span v-if="res.durationMinutes">
                        预计 {{ res.durationMinutes }} 分钟
                      </span>
                      <span v-if="res.tags">标签：{{ res.tags }}</span>
                    </div>
                    <div v-if="resourceQualityParts(res).length" class="resource-quality-row">
                      <n-tag
                        v-for="part in resourceQualityParts(res)"
                        :key="part"
                        size="tiny"
                        type="default"
                      >
                        {{ part }}
                      </n-tag>
                    </div>
                    <div v-if="res.reason" class="resource-reason">
                      推荐理由：{{ res.reason }}
                    </div>
                    <div
                      v-if="res.id"
                      class="resource-feedback-row"
                    >
                      <span class="resource-feedback-label">你的感觉：</span>
                      <n-button
                        size="tiny"
                        text
                        :disabled="getFeedbackState(res.id).loading"
                        @click="handleResourceFeedback(res, 'helpful')"
                      >
                        👍 有帮助
                      </n-button>
                      <n-button
                        size="tiny"
                        text
                        type="error"
                        :disabled="getFeedbackState(res.id).loading"
                        @click="handleResourceFeedback(res, 'invalid')"
                      >
                        👎 不相关 / 无效
                      </n-button>
                      <n-tag v-if="getFeedbackState(res.id).value" size="small" type="info">
                        已反馈：{{ formatResourceFeedbackLabel(getFeedbackState(res.id).value) }}
                      </n-tag>
                    </div>
                  </li>
                </ul>

                <p
                  v-if="
                    currentDay.id && dayExercisesMap[currentDay.id]?.error
                  "
                  class="error-text"
                >
                  {{ dayExercisesMap[currentDay.id].error }}
                </p>

                <p
                  v-else-if="
                    currentDay.id &&
                    dayExercisesMap[currentDay.id]?.loadedOnce &&
                    !dayExercisesMap[currentDay.id]?.loading &&
                    dayExercisesMap[currentDay.id]?.items &&
                    dayExercisesMap[currentDay.id].items.length === 0
                  "
                  class="helper-text"
                >
                  当前暂未生成练习题，可以稍后重试或更换学习主题。
                </p>

                <ul
                  v-if="
                    currentDay.id &&
                    dayExercisesMap[currentDay.id]?.items &&
                    dayExercisesMap[currentDay.id].items.length
                  "
                  class="exercise-list"
                >
                  <li
                    v-for="(q, qIdx) in dayExercisesMap[currentDay.id].items"
                    :key="qIdx"
                    class="exercise-item"
                  >
                    <div class="exercise-question">
                      练习 {{ qIdx + 1 }}：{{ q.question }}
                    </div>
                    <div
                      v-if="q.difficulty || q.skillFocus"
                      class="exercise-meta"
                    >
                      <span v-if="q.difficulty">难度：{{ q.difficulty }}</span>
                      <span v-if="q.skillFocus">考察点：{{ q.skillFocus }}</span>
                    </div>
                    <textarea
                      class="exercise-answer-input"
                      rows="3"
                      placeholder="写下你的答案，提交后会先由 AI 评测，再自动保存到服务端。"
                      v-model="dayExercisesMap[currentDay.id].answers[qIdx]"
                    ></textarea>
                    <div class="exercise-actions-row">
                      <n-button
                        size="tiny"
                        quaternary
                        :loading="isExerciseSubmitting(currentDay.id, qIdx)"
                        @click="saveExerciseForDay(currentDay, qIdx)"
                      >
                        {{
                          isExerciseSubmitting(currentDay.id, qIdx)
                            ? '正在评测并保存…'
                            : '评测并保存作答'
                        }}
                      </n-button>
                    </div>
                    <div
                      v-if="getExerciseResult(currentDay.id, qIdx)"
                      class="exercise-feedback-card"
                    >
                      <div class="exercise-feedback-header">
                        <n-tag
                          v-if="getExerciseResult(currentDay.id, qIdx)?.score != null"
                          size="small"
                          :type="scoreTagType(getExerciseResult(currentDay.id, qIdx)?.score)"
                        >
                          得分 {{ getExerciseResult(currentDay.id, qIdx)?.score }}
                        </n-tag>
                        <span
                          v-if="getExerciseResult(currentDay.id, qIdx)?.mistakeType"
                          class="exercise-feedback-type"
                        >
                          {{ formatMistakeType(getExerciseResult(currentDay.id, qIdx)?.mistakeType) }}
                        </span>
                      </div>
                      <div
                        v-if="getExerciseResult(currentDay.id, qIdx)?.feedback"
                        class="exercise-feedback-text"
                      >
                        {{ getExerciseResult(currentDay.id, qIdx)?.feedback }}
                      </div>
                      <div
                        v-if="getExerciseResult(currentDay.id, qIdx)?.nextRecommendation"
                        class="exercise-feedback-next"
                      >
                        下一步建议：{{ getExerciseResult(currentDay.id, qIdx)?.nextRecommendation }}
                      </div>
                    </div>
                    <div
                      v-else-if="dayExercisesMap[currentDay.id]?.submittingIndex === qIdx"
                      class="helper-text"
                    >
                      正在生成本题评测反馈，请稍候…
                    </div>
                    <div
                      v-if="dayExercisesMap[currentDay.id]?.lastSavedAt && dayExercisesMap[currentDay.id]?.lastSubmittedIndex === qIdx && dayExercisesMap[currentDay.id]?.submittingIndex !== qIdx"
                      class="helper-text exercise-saved-hint"
                    >
                      本题最近一次保存时间：{{ dayExercisesMap[currentDay.id].lastSavedAt }}
                    </div>
                    <div
                      v-if="dayExercisesMap[currentDay.id]?.saveError && dayExercisesMap[currentDay.id]?.lastSubmittedIndex === qIdx && dayExercisesMap[currentDay.id]?.submittingIndex == null"
                      class="error-text"
                    >
                      {{ dayExercisesMap[currentDay.id].saveError }}
                    </div>
                    <div
                      v-if="dayExercisesMap[currentDay.id]?.saveSuccessMessage && dayExercisesMap[currentDay.id]?.lastSubmittedIndex === qIdx && dayExercisesMap[currentDay.id]?.submittingIndex == null"
                      class="helper-text exercise-save-success"
                    >
                      {{ dayExercisesMap[currentDay.id].saveSuccessMessage }}
                    </div>
                    <details class="exercise-details">
                      <summary>查看参考答案与讲解</summary>
                      <div class="exercise-answer-ref">
                        <strong>参考答案：</strong>{{ q.answer }}
                      </div>
                      <div
                        v-if="q.explanation"
                        class="exercise-explanation"
                      >
                        <strong>讲解：</strong>{{ q.explanation }}
                      </div>
                    </details>
                  </li>
                </ul>


              </div>
            </div>
            </div>

            <p v-else class="helper-text">
              当前计划暂无具体天数数据。
            </p>
          </n-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  deletePlan,
  getPlanById,
  getRecentPlans,
  getResourcesByPlan,
  replanPlan,
  updatePlan
} from '../api/plan';
import { usePlanStudyActions } from '../composables/usePlanStudyActions';
import { useResourceFeedback } from '../composables/useResourceFeedback';
import { useAuthStore } from '../store/auth';
import { buildResourceQualityParts } from '../utils/resource';

const plans = ref([]);
const router = useRouter();
const { currentUser } = useAuthStore();
const {
  hydrateResourceFeedback,
  sendResourceFeedback,
  getFeedbackState,
  formatResourceFeedbackLabel
} = useResourceFeedback(currentUser);
const {
  dayResourcesMap,
  dayStatusSavingMap,
  dayRefineMap,
  dayExercisesMap,
  loadDayResources,
  loadDayExercises,
  markDayCompleted,
  refineDayTasks,
  saveExerciseForDay,
  getExerciseResult,
  isExerciseSubmitting,
  scoreTagType,
  formatMistakeType
} = usePlanStudyActions(currentUser, {
  onDayResourcesLoaded: hydrateResourceFeedback
});

const currentPlan = ref(null);
const currentDayId = ref(null);
const listLoading = ref(false);
const detailLoading = ref(false);
const listError = ref('');
const detailError = ref('');
const showAllPlans = ref(false);
const rememberedDayMap = ref({});

const planResources = reactive({
  loading: false,
  error: '',
  items: [],
  loadedOnce: false
});
const dayReplanMap = reactive({});

onMounted(async () => {
  rememberedDayMap.value = loadRememberedDayMap();
  await loadRecentPlans();
});

async function loadRecentPlans() {
  listError.value = '';
  listLoading.value = true;

  try {
    const userId = currentUser.value ? currentUser.value.id : null;
    const data = await getRecentPlans(50, userId);
    plans.value = data;
    showAllPlans.value = false;

    if (plans.value.length > 0) {
      await loadPlanDetail(plans.value[0].id);
    } else {
      currentPlan.value = null;
      currentDayId.value = null;
    }
  } catch (e) {
    console.error(e);
    listError.value = '加载历史计划失败，请确认后端服务已启动后再重试。';
  } finally {
    listLoading.value = false;
  }
}

const selectedPlanId = computed(() => {
  return getPlanIdentity(currentPlan.value) || null;
});

const planSelectOptions = computed(() =>
  plans.value.map((item) => ({
    label: item.title || `学习计划 #${item.id}`,
    value: String(item.id)
  }))
);

const visiblePlans = computed(() => {
  if (showAllPlans.value) {
    return plans.value;
  }

  const currentId = selectedPlanId.value;
  const base = plans.value.slice(0, 4);
  if (!currentId) {
    return base;
  }

  const alreadyIncluded = base.some((item) => String(item.id) === currentId);
  if (alreadyIncluded) {
    return base;
  }

  const currentItem = plans.value.find((item) => String(item.id) === currentId);
  if (!currentItem) {
    return base;
  }

  return [currentItem, ...base.slice(0, 3)];
});

const collapsedPlanHint = computed(() => {
  if (showAllPlans.value || plans.value.length <= visiblePlans.value.length) {
    return '';
  }
  const hiddenCount = plans.value.length - visiblePlans.value.length;
  return `已收起其余 ${hiddenCount} 条计划，避免把日程索引挤到下方。`;
});

async function onSelectPlan(id) {
  await loadPlanDetail(id);
}

async function loadPlanDetail(id) {
  detailError.value = '';
  detailLoading.value = true;

  try {
    const userId = currentUser.value ? currentUser.value.id : null;
    const data = await getPlanById(id, userId);
    currentPlan.value = data;
    planResources.loading = false;
    planResources.error = '';
    planResources.items = [];
    planResources.loadedOnce = false;
    currentDayId.value = resolveInitialDayId(data);
  } catch (e) {
    console.error(e);
    detailError.value = '加载计划详情失败，请稍后重试。';
  } finally {
    detailLoading.value = false;
  }
}

const totalDays = computed(() => currentPlan.value?.days?.length ?? 0);

const completedDays = computed(() =>
  currentPlan.value?.days
    ? currentPlan.value.days.filter((d) => d.status === 'completed').length
    : 0
);

const completionRate = computed(() =>
  totalDays.value > 0
    ? Math.round((completedDays.value * 100) / totalDays.value)
    : 0
);

const currentDay = computed(() => {
  if (!currentPlan.value?.days || !currentDayId.value) return null;
  return (
    currentPlan.value.days.find(
      (d) => String(d.id) === String(currentDayId.value)
    ) || null
  );
});

function onSelectDay(dayId) {
  currentDayId.value = dayId;
  rememberDayForCurrentPlan(dayId);
}

async function handleQuickSelectPlan(id) {
  if (!id) return;
  await onSelectPlan(id);
}

function getPlanIdentity(planLike) {
  if (!planLike) return null;
  return String(planLike.planId || planLike.plan_id || planLike.id || '');
}

function getDayMemoryStorageKey() {
  const userId = currentUser.value?.id ?? 'guest';
  return `learnflow:plan-history:last-day:${userId}`;
}

function loadRememberedDayMap() {
  if (typeof window === 'undefined') return {};
  try {
    const raw = window.sessionStorage.getItem(getDayMemoryStorageKey());
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (error) {
    console.warn('读取历史页日程定位缓存失败', error);
    return {};
  }
}

function persistRememberedDayMap() {
  if (typeof window === 'undefined') return;
  try {
    window.sessionStorage.setItem(
      getDayMemoryStorageKey(),
      JSON.stringify(rememberedDayMap.value)
    );
  } catch (error) {
    console.warn('保存历史页日程定位缓存失败', error);
  }
}

function resolveInitialDayId(plan) {
  const days = Array.isArray(plan?.days) ? plan.days : [];
  if (!days.length) {
    return null;
  }

  const planId = getPlanIdentity(plan);
  const rememberedDayId = planId ? rememberedDayMap.value[String(planId)] : null;
  if (rememberedDayId) {
    const matchedDay = days.find((day) => String(day.id) === String(rememberedDayId));
    if (matchedDay) {
      return matchedDay.id;
    }
  }

  return days[0].id;
}

function rememberDayForCurrentPlan(dayId) {
  const planId = getPlanIdentity(currentPlan.value);
  if (!planId || !dayId) return;
  rememberedDayMap.value = {
    ...rememberedDayMap.value,
    [String(planId)]: String(dayId)
  };
  persistRememberedDayMap();
}

function formatDate(dateStr) {
  if (!dateStr) return '';
  return dateStr;
}

function statusText(status) {
  if (!status) return '未开始';
  const map = {
    active: '进行中',
    completed: '已完成',
    cancelled: '已取消',
    not_started: '未开始',
    in_progress: '进行中',
    delayed: '已延迟'
  };
  return map[status] || status;
}

function formatResourceDomain(domain) {
  const value = String(domain || '').toLowerCase();
  const map = {
    java: 'Java 后端',
    python: 'Python',
    database: '数据库 / SQL',
    english: '英语',
    math: '数学',
    frontend: '前端',
    devops: 'Linux / 运维',
    general: '通用学习方法'
  };
  return map[value] || domain || '';
}

function goToResourceUpload() {
  router.push('/upload-resource');
}

function statusTagType(status) {
  const map = {
    active: 'info',
    completed: 'success',
    cancelled: 'default',
    not_started: 'default',
    in_progress: 'info',
    delayed: 'warning'
  };
  return map[status] || 'default';
}

function resourceQualityParts(resource) {
  return buildResourceQualityParts(resource);
}

async function loadPlanResourcesForCurrentPlan() {
  if (!currentPlan.value) return;
  const planId = currentPlan.value.planId || currentPlan.value.plan_id || currentPlan.value.id;
  if (!planId) return;

  planResources.loading = true;
  planResources.error = '';
  planResources.items = [];

  try {
    const userId = currentUser.value ? currentUser.value.id : undefined;
    const items = await getResourcesByPlan(planId, userId);
    planResources.items = hydrateResourceFeedback(items || []);
    planResources.loadedOnce = true;
  } catch (e) {
    console.error(e);
    planResources.error = '加载计划推荐资源失败，请稍后重试。';
  } finally {
    planResources.loading = false;
  }
}

function handleResourceFeedback(resource, value) {
  if (value === 'helpful' || value === 'invalid') {
    sendResourceFeedback(resource, value).catch(() => {});
  }
}

async function replanCurrentDay() {
  if (!currentPlan.value || !currentDay.value?.id) return;

  const planId = currentPlan.value.planId || currentPlan.value.plan_id || currentPlan.value.id;
  const userId = currentUser.value ? currentUser.value.id : null;
  if (!planId || !userId) return;

  const reason = window.prompt(
    '请输入顺延原因（可选）',
    '今天任务未完成，顺延 1 天并重排后续计划'
  );
  if (reason === null) {
    return;
  }

  const dayId = currentDay.value.id;
  if (!dayReplanMap[dayId]) {
    dayReplanMap[dayId] = {
      loading: false,
      error: ''
    };
  }

  const state = dayReplanMap[dayId];
  state.loading = true;
  state.error = '';

  try {
    const data = await replanPlan(planId, {
      userId,
      triggerDayId: dayId,
      delayDays: 1,
      reason: reason.trim() || null
    });
    currentPlan.value = data;
    currentDayId.value = resolveInitialDayId(data) || dayId;
    rememberDayForCurrentPlan(currentDayId.value);
    planResources.loadedOnce = false;
    planResources.items = [];
  } catch (e) {
    console.error(e);
    state.error = '顺延并重排失败，请稍后重试。';
  } finally {
    state.loading = false;
  }
}

async function renameCurrentPlan() {
  if (!currentPlan.value) return;
  const newTitle = window.prompt(
    '请输入新的计划名称',
    currentPlan.value.title || ''
  );
  if (!newTitle || !newTitle.trim()) return;

  const userId = currentUser.value ? currentUser.value.id : null;
  try {
    const planId =
      currentPlan.value.planId ||
      currentPlan.value.plan_id ||
      currentPlan.value.id;
    await updatePlan(planId, userId, { title: newTitle.trim() });
    currentPlan.value.title = newTitle.trim();
    const idx = plans.value.findIndex((p) => String(p.id) === String(planId));
    if (idx >= 0) {
      plans.value[idx].title = newTitle.trim();
    }
  } catch (e) {
    console.error(e);
    window.alert('更新计划名称失败，请稍后重试。');
  }
}

async function deleteCurrentPlan() {
  if (!currentPlan.value) return;
  const confirmed = window.confirm('确认删除当前计划吗？删除后将无法继续在历史页中查看。');
  if (!confirmed) return;

  const userId = currentUser.value ? currentUser.value.id : null;

  try {
    const planId =
      currentPlan.value.planId ||
      currentPlan.value.plan_id ||
      currentPlan.value.id;
    await deletePlan(planId, userId);
    const nextMemory = { ...rememberedDayMap.value };
    delete nextMemory[String(planId)];
    rememberedDayMap.value = nextMemory;
    persistRememberedDayMap();
    currentPlan.value = null;
    currentDayId.value = null;
    await loadRecentPlans();
  } catch (e) {
    console.error(e);
    window.alert('删除计划失败，请稍后重试。');
  }
}
</script>

<style scoped>
.plan-history-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.history-header {
  border-bottom: none;
}

.history-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.75fr);
  gap: 16px;
  align-items: stretch;
  padding: 22px 24px;
  border-radius: 24px;
  border: 1px solid rgba(17, 42, 59, 0.08);
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.82), transparent 35%),
    linear-gradient(135deg, #f9fcfc, #f1f6f4 55%, #eef3f8);
}

.history-kicker,
.panel-kicker {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  color: #6f8191;
}

.history-title {
  margin-top: 6px;
  color: #102235;
}

.history-subtitle {
  margin-bottom: 0;
  max-width: 760px;
  line-height: 1.75;
}

.history-focus-card {
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, #173850, #2b6661 125%);
  color: #f8fafc;
  box-shadow: 0 16px 28px rgba(15, 41, 64, 0.16);
}

.history-focus-label {
  font-size: 12px;
  color: rgba(243, 249, 251, 0.72);
}

.history-focus-title {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.25;
}

.history-focus-meta {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: rgba(243, 249, 251, 0.84);
}

.history-stats {
  margin-top: 2px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  min-width: 0;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff, #f7fbfb);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
}

.stat-value {
  margin-top: 4px;
  font-size: 26px;
  font-weight: 700;
  color: #102235;
}

.stat-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #7c8b98;
}

.history-main-row {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1.6fr);
  align-items: start;
  gap: 14px;
}

.left-column,
.right-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.history-sidebar-card {
  overflow: hidden;
}

.block-card {
  border-radius: 20px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.detail-card {
  margin-top: 0;
}

.right-column {
  align-self: start;
}

.right-workbench {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.workbench-sticky-bar {
  position: sticky;
  top: 0;
  z-index: 6;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.86), transparent 42%),
    linear-gradient(135deg, rgba(248, 252, 252, 0.96), rgba(241, 246, 244, 0.98) 55%, rgba(238, 243, 248, 0.98));
  backdrop-filter: blur(10px);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}

.workbench-sticky-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.workbench-sticky-title {
  font-size: 18px;
  line-height: 1.4;
  font-weight: 700;
  color: #102235;
}

.workbench-sticky-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: #607483;
}

.workbench-sticky-stats {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.workbench-sticky-chip {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 104px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.workbench-sticky-chip-label {
  font-size: 11px;
  color: #738496;
}

.workbench-sticky-chip strong {
  font-size: 13px;
  line-height: 1.5;
  color: #102235;
}

.workbench-card {
  flex: 0 0 auto;
}

.workbench-card-detail {
  flex: 1 1 auto;
  min-height: 0;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-title,
.detail-title {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
  color: #102235;
}

.list-subtitle {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.7;
}

.detail-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sidebar-current-plan {
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.8), transparent 42%),
    linear-gradient(135deg, #18374e, #2d675d 125%);
  color: #f8fafc;
  box-shadow: 0 14px 28px rgba(15, 41, 64, 0.16);
}

.sidebar-current-label,
.sidebar-section-kicker {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.sidebar-current-label {
  color: rgba(243, 249, 251, 0.72);
}

.sidebar-current-title {
  margin-top: 8px;
  font-size: 18px;
  line-height: 1.45;
  font-weight: 700;
}

.sidebar-current-meta {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: rgba(243, 249, 251, 0.84);
}

.sidebar-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sidebar-section {
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: linear-gradient(180deg, #fcfdfd, #f7fafb);
}

.sidebar-section-days {
  background:
    radial-gradient(circle at top right, rgba(219, 234, 254, 0.5), transparent 34%),
    linear-gradient(180deg, #fbfdff, #f5f9ff);
}

.sidebar-section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.sidebar-section-kicker {
  color: #6f8191;
}

.sidebar-section-title {
  margin-top: 4px;
  font-size: 16px;
  font-weight: 700;
  color: #102235;
}

.sidebar-section-note {
  font-size: 12px;
  color: #738496;
}

.plan-switch-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.collapsed-hint,
.day-index-summary {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fbfb, #f3f7f7);
  border: 1px solid rgba(148, 163, 184, 0.18);
  font-size: 12px;
  line-height: 1.7;
  color: #607483;
}

.day-index-summary {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.plan-list-shell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.plan-list-shell-expanded {
  max-height: 280px;
  overflow-y: auto;
  padding-right: 4px;
}

.plan-item {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  cursor: pointer;
  transition:
    transform 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    background 0.16s ease;
}

.plan-item:hover {
  transform: translateY(-1px);
  border-color: rgba(42, 103, 96, 0.34);
  box-shadow: 0 12px 22px rgba(15, 23, 42, 0.08);
}

.plan-item-active {
  background: linear-gradient(180deg, #f4f8ff, #eef6ff);
  border-color: rgba(59, 130, 246, 0.32);
  box-shadow: 0 12px 24px rgba(59, 130, 246, 0.12);
}

.plan-item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.plan-item-title {
  font-size: 15px;
  font-weight: 700;
  color: #102235;
  line-height: 1.5;
}

.plan-item-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: #6b7280;
}

.day-brief {
  padding: 12px 14px;
  border-radius: 14px;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  align-items: center;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: linear-gradient(180deg, #fbfcfd, #f7fafb);
  transition:
    transform 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    background 0.16s ease;
}

.day-brief-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: min(52vh, 520px);
  overflow-y: auto;
  padding-right: 4px;
}

.day-brief:hover {
  transform: translateY(-1px);
  border-color: rgba(42, 103, 96, 0.28);
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.06);
}

.day-brief-active {
  background: linear-gradient(180deg, #f5f9ff, #eef6ff);
  border-color: rgba(59, 130, 246, 0.28);
}

.day-brief-date {
  font-size: 12px;
  color: #6b7280;
}

.day-brief-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.day-brief-title {
  font-size: 14px;
  color: #102235;
  font-weight: 600;
  line-height: 1.5;
}

.plan-resources-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #102235;
}

.resources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}

.resource-card,
.resource-item {
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.resource-card {
  padding: 14px;
}

.resources-list {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.resource-item {
  padding: 14px;
}

.resource-title-row {
  margin-bottom: 6px;
}

.resource-title {
  font-weight: 700;
  color: #102235;
  text-decoration: none;
}

.resource-title:hover {
  color: #1f5f68;
}

.resource-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
}

.resource-quality-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.resource-reason {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: #4b5563;
}

.resource-empty-state {
  margin-top: 10px;
  padding: 18px 16px;
  border-radius: 18px;
  border: 1px dashed rgba(37, 99, 235, 0.28);
  background:
    radial-gradient(circle at top left, rgba(219, 234, 254, 0.68), transparent 34%),
    linear-gradient(180deg, #fbfdff, #f6f9ff);
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.resource-empty-state-compact {
  margin-top: 12px;
}

.resource-empty-icon {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2563eb, #22c55e);
  color: #ffffff;
  font-size: 22px;
  line-height: 1;
  box-shadow: 0 10px 20px rgba(59, 130, 246, 0.18);
}

.resource-empty-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.resource-empty-desc {
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
  max-width: 520px;
}

.resource-empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.resource-feedback-row {
  margin-top: 10px;
  font-size: 12px;
  color: #6b7280;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.resource-feedback-label {
  margin-right: 2px;
}

.day-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.day-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  padding-bottom: 12px;
  border-bottom: 1px dashed rgba(148, 163, 184, 0.36);
}

.day-detail-title {
  font-size: 22px;
  font-weight: 700;
  color: #102235;
  line-height: 1.35;
}

.day-detail-actions,
.detail-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.day-detail-body {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1.9fr);
  gap: 14px;
}

.sub-section {
  min-width: 0;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: linear-gradient(180deg, #fbfcfd, #f8fafc);
}

.sub-section-title {
  font-size: 16px;
  font-weight: 700;
  color: #102235;
  margin-bottom: 10px;
}

.task-list {
  padding-left: 18px;
  margin: 0;
  font-size: 13px;
  color: #374151;
  line-height: 1.8;
}

.exercise-list {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.exercise-item {
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfcfd, #f7fafb);
  border: 1px solid rgba(148, 163, 184, 0.22);
}

.exercise-question {
  font-size: 15px;
  font-weight: 700;
  color: #102235;
  margin-bottom: 6px;
  line-height: 1.7;
}

.exercise-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 10px;
}

.exercise-answer-input {
  width: 100%;
  margin-top: 6px;
  font-size: 13px;
  padding: 10px 12px;
  line-height: 1.7;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.34);
  background: #ffffff;
  resize: vertical;
}

.exercise-feedback-card {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #eff6ff;
  border: 1px solid rgba(96, 165, 250, 0.24);
}

.exercise-feedback-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.exercise-feedback-type {
  font-size: 12px;
  color: #b45309;
  font-weight: 600;
}

.exercise-feedback-text,
.exercise-feedback-next {
  font-size: 13px;
  color: #1f2937;
  line-height: 1.75;
  white-space: pre-wrap;
}

.exercise-feedback-next {
  margin-top: 6px;
}

.exercise-actions-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.exercise-saved-hint,
.exercise-save-success {
  margin-top: 6px;
}

.exercise-details {
  margin-top: 12px;
  font-size: 12px;
}

.exercise-answer-ref,
.exercise-explanation {
  margin-top: 8px;
  line-height: 1.75;
  color: #374151;
}

@media (max-width: 960px) {
  .history-hero,
  .history-stats,
  .history-main-row,
  .day-detail-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .panel-header,
  .plan-item-header,
  .plan-item-meta,
  .day-brief-main,
  .section-header,
  .day-detail-header {
    flex-direction: column;
  }

  .plan-switch-bar {
    grid-template-columns: minmax(0, 1fr);
  }

  .detail-actions,
  .day-detail-actions {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .history-sidebar-card {
    position: static;
  }

  .right-column {
    position: static;
  }

  .right-workbench {
    padding-right: 0;
  }

  .workbench-sticky-bar {
    position: static;
    flex-direction: column;
  }

  .workbench-sticky-stats {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>


















