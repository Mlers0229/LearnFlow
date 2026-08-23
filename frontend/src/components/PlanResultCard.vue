<template>
  <n-card
    v-if="plan"
    class="plan-section plan-result-card"
    :bordered="true"
    :segmented="{ content: true, footer: false }"
    hoverable
  >
    <template #header>
      <div class="plan-header">
        <div class="plan-header-left">
          <div class="plan-title">
            {{ plan.title }}
          </div>
          <div class="plan-meta">
            <span class="plan-id mono">
              计划 ID：{{ plan.planId || plan.plan_id || plan.id }}
            </span>
            <span class="plan-date">
              时间：{{ formatDate(plan.startDate || plan.start_date) }} ~
              {{ formatDate(plan.endDate || plan.end_date) }}
            </span>
          </div>
        </div>

        <div v-if="progress.loaded" class="plan-progress">
          <div class="plan-progress-row">
            <span class="plan-progress-label">
              完成度：{{ progress.completionRate }}%
              （{{ progress.completedDays }}/{{ progress.totalDays }} 天）
            </span>
          </div>
          <n-progress
            type="line"
            :percentage="progress.completionRate"
            :show-indicator="false"
            :height="8"
          />
        </div>
      </div>
    </template>

    <p class="helper-text top-hint">
      以下计划已保存，可在「历史计划」中再次查看和操作。
    </p>

    <div v-if="adaptation" class="adaptation-banner">
      <n-tag size="small" :type="adaptation.applied ? 'success' : 'default'">
        {{ adaptation.applied ? '已启用掌握度自适应' : '固定策略' }}
      </n-tag>
      <span>{{ adaptationDescription }}</span>
    </div>

    <div class="plan-snapshot-grid">
      <div
        v-for="item in planSnapshotCards"
        :key="item.label"
        class="plan-snapshot-card"
      >
        <div class="plan-snapshot-label">{{ item.label }}</div>
        <div class="plan-snapshot-value">{{ item.value }}</div>
        <div class="plan-snapshot-meta">{{ item.meta }}</div>
      </div>
    </div>

    <div v-if="nextPendingDay" class="plan-next-step">
      <div class="plan-next-step-label">下一步建议</div>
      <div class="plan-next-step-value">
        {{ formatDate(nextPendingDay.date) }} · {{ nextPendingDay.title }}
      </div>
      <div class="plan-next-step-meta">
        {{ dayMetaParts(nextPendingDay).join(' · ') || '继续推进当前学习节奏' }}
      </div>
    </div>

    <div
      v-if="traceId || goalBlueprint || planPhases.length || planWeeks.length || validationReport"
      class="plan-v2-overview"
    >
      <div class="section-header overview-header">
        <div class="section-title">学习计划 v2 规划视图</div>
        <div class="section-subtitle">
          把目标拆解、阶段安排、周节奏和校验结果一起展示出来，方便判断这份计划是否真正可执行。
        </div>
      </div>

      <div class="plan-v2-grid">
        <n-card v-if="goalBlueprint || traceId" size="small" class="overview-panel">
          <div class="sub-section-title">目标蓝图</div>
          <div class="overview-meta-row">
            <n-tag v-if="traceId" size="small" type="default">
              traceId：{{ traceIdShort }}
            </n-tag>
            <n-tag
              v-if="goalBlueprint && (goalBlueprint.targetRole || goalBlueprint.target_role)"
              size="small"
              type="info"
            >
              目标方向：{{ goalBlueprint.targetRole || goalBlueprint.target_role }}
            </n-tag>
            <a v-if="traceId" :href="agentLogHref" class="trace-link">
              查看 Agent 调用链
            </a>
          </div>
          <p v-if="goalBlueprint && goalBlueprint.summary" class="helper-text overview-summary">
            {{ goalBlueprint.summary }}
          </p>

          <div v-if="goalTopics.length" class="blueprint-topic-list">
            <div
              v-for="(topic, idx) in goalTopics"
              :key="topic.id || topic.name || idx"
              class="blueprint-topic-item"
            >
              <div class="blueprint-topic-order">{{ topic.order || idx + 1 }}</div>
              <div class="blueprint-topic-body">
                <div class="blueprint-topic-title-row">
                  <span class="blueprint-topic-title">{{ topic.name }}</span>
                  <span v-if="topic.difficulty" class="blueprint-topic-meta">{{ topic.difficulty }}</span>
                </div>
                <div v-if="topic.description" class="blueprint-topic-desc">
                  {{ topic.description }}
                </div>
                <div class="blueprint-topic-meta-row">
                  <span v-if="topic.estimatedDays || topic.estimated_days">
                    预计 {{ topic.estimatedDays || topic.estimated_days }} 天
                  </span>
                  <span v-if="topic.practiceType || topic.practice_type">
                    建议方式：{{ topic.practiceType || topic.practice_type }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="goalMilestones.length" class="blueprint-milestone-block">
            <div class="sub-section-title compact-title">阶段里程碑</div>
            <ul class="blueprint-milestone-list">
              <li
                v-for="(milestone, idx) in goalMilestones"
                :key="milestone.title || idx"
              >
                <strong>{{ milestone.title }}</strong>
                <span v-if="milestone.suggestedWeek || milestone.suggested_week">
                  （建议第 {{ milestone.suggestedWeek || milestone.suggested_week }} 周）
                </span>
                ：{{ milestone.description }}
              </li>
            </ul>
          </div>
        </n-card>

        <n-card v-if="validationReport" size="small" class="overview-panel validation-panel">
          <div class="sub-section-title">计划校验结果</div>
          <div class="validation-status-row">
            <n-tag
              size="small"
              :type="(validationReport.isValid ?? validationReport.is_valid) === false ? 'warning' : 'success'"
            >
              {{ (validationReport.isValid ?? validationReport.is_valid) === false ? '存在需要关注的问题' : '基础校验通过' }}
            </n-tag>
          </div>
          <div class="validation-score-grid">
            <div class="validation-score-item">
              <div class="validation-score-label">覆盖度</div>
              <n-tag size="small" :type="validationScoreTagType(validationReport.coverageScore ?? validationReport.coverage_score)">
                {{ validationReport.coverageScore ?? validationReport.coverage_score ?? '--' }}
              </n-tag>
            </div>
            <div class="validation-score-item">
              <div class="validation-score-label">重复度</div>
              <n-tag size="small" :type="validationScoreTagType(validationReport.repetitionScore ?? validationReport.repetition_score)">
                {{ validationReport.repetitionScore ?? validationReport.repetition_score ?? '--' }}
              </n-tag>
            </div>
            <div class="validation-score-item">
              <div class="validation-score-label">负载均衡</div>
              <n-tag size="small" :type="validationScoreTagType(validationReport.loadBalanceScore ?? validationReport.load_balance_score)">
                {{ validationReport.loadBalanceScore ?? validationReport.load_balance_score ?? '--' }}
              </n-tag>
            </div>
          </div>

          <div v-if="validationIssues.length || validationWarnings.length" class="validation-issue-block">
            <div class="sub-section-title compact-title">问题清单</div>
            <ul class="validation-issue-list">
              <li
                v-for="(issue, idx) in [...validationIssues, ...validationWarnings]"
                :key="issue.code || idx"
              >
                <n-tag size="tiny" :type="validationIssueTagType(issue.severity)">
                  {{ issue.severity || 'info' }}
                </n-tag>
                <span>
                  {{ issue.message }}
                  <template v-if="issue.dayIndex || issue.day_index">
                    （关联第 {{ issue.dayIndex || issue.day_index }} 天）
                  </template>
                </span>
              </li>
            </ul>
          </div>

          <div v-if="validationSuggestedFixes.length" class="validation-fix-block">
            <div class="sub-section-title compact-title">优化建议</div>
            <ul class="validation-fix-list">
              <li v-for="(fix, idx) in validationSuggestedFixes" :key="idx">
                {{ fix }}
              </li>
            </ul>
          </div>
        </n-card>
      </div>

      <n-card v-if="planPhases.length" size="small" class="overview-panel">
        <div class="sub-section-title">阶段拆解</div>
        <div class="phase-grid">
          <div
            v-for="(phase, idx) in planPhases"
            :key="phase.phaseId || phase.phase_id || idx"
            class="phase-card"
          >
            <div class="phase-card-title-row">
              <div class="phase-card-title">{{ phase.title }}</div>
              <n-tag size="tiny" type="info">
                {{ phase.weeks }} 周
              </n-tag>
            </div>
            <div v-if="phase.goal" class="phase-card-goal">
              {{ phase.goal }}
            </div>
            <div v-if="getPhaseFocusTopics(phase).length" class="phase-card-meta">
              聚焦主题：{{ getPhaseFocusTopics(phase).join(' / ') }}
            </div>
            <div v-if="phase.expectedOutcome || phase.expected_outcome" class="phase-card-outcome">
              预期产出：{{ phase.expectedOutcome || phase.expected_outcome }}
            </div>
          </div>
        </div>
      </n-card>

      <n-card v-if="planWeeks.length" size="small" class="overview-panel">
        <div class="sub-section-title">周计划节奏</div>
        <div class="week-list">
          <div
            v-for="(week, idx) in planWeeks"
            :key="week.weekIndex || week.week_index || idx"
            class="week-item"
          >
            <div class="week-item-header">
              <div class="week-item-title">
                第 {{ week.weekIndex || week.week_index }} 周 · {{ week.theme }}
              </div>
              <n-tag size="tiny" type="default">
                {{ week.targetHours || week.target_hours }} 小时
              </n-tag>
            </div>
            <div v-if="getWeekFocusTopics(week).length" class="week-item-meta">
              聚焦：{{ getWeekFocusTopics(week).join(' / ') }}
            </div>
            <div v-if="week.milestone" class="week-item-meta">
              里程碑：{{ week.milestone }}
            </div>
            <div v-if="week.reviewStrategy || week.review_strategy" class="week-item-meta">
              复习策略：{{ week.reviewStrategy || week.review_strategy }}
            </div>
          </div>
        </div>
      </n-card>
    </div>

    <div class="plan-body">
      <div class="plan-resources">
        <div class="section-header">
          <div class="section-title">整份计划的推荐学习资源</div>
          <n-button
            size="small"
            quaternary
            type="primary"
            :loading="planResources.loading"
            @click="loadPlanResources"
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
            <n-button size="small" secondary @click="loadPlanResources">
              重新获取推荐
            </n-button>
          </div>
        </div>

        <div
          v-if="planResources.items && planResources.items.length"
          class="resources-grid global-resources-list"
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
              <span v-if="res.durationMinutes">预计 {{ res.durationMinutes }} 分钟</span>
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
            <ResourceEvidenceList
              :evidence="res.evidence"
              :evidence-status="res.evidenceStatus"
              :confidence="res.confidence"
            />
            <div v-if="res.id" class="resource-feedback-row">
              <span class="resource-feedback-label">觉得这个资源怎么样？</span>
              <n-radio-group
                size="small"
                :disabled="getFeedbackState(res.id).loading"
                :value="getFeedbackState(res.id).value"
                @update:value="(val) => handleFeedbackChange(res, val)"
              >
                <n-radio value="helpful">👍 有帮助</n-radio>
                <n-radio value="invalid">👎 不相关 / 无效</n-radio>
              </n-radio-group>
              <n-tag v-if="getFeedbackState(res.id).value" size="small" type="info">
                已反馈：{{ formatResourceFeedbackLabel(getFeedbackState(res.id).value) }}
              </n-tag>
            </div>
          </n-card>
        </div>
      </div>

      <div class="plan-days">
        <div class="section-header days-header">
          <div class="section-title">每日任务与资源</div>
          <div class="section-subtitle">
            每天可以：标记完成、细化任务、查看当日资源、生成练习题。
          </div>
        </div>

        <n-space vertical size="small">
          <n-card
            v-for="(day, index) in plan.days"
            :key="day.id || day.date || index"
            size="small"
            class="day-card"
          >
            <div class="day-header">
              <div class="day-header-main">
                <div class="day-date mono">
                  {{ formatDate(day.date) }}
                </div>
                <div class="day-title">
                  {{ day.title }}
                </div>
                <div v-if="dayMetaParts(day).length" class="day-submeta">
                  {{ dayMetaParts(day).join(' · ') }}
                </div>
              </div>
              <div class="day-header-right">
                <n-tag
                  size="small"
                  :type="statusTagType(day.status)"
                  class="day-status-tag"
                >
                  {{ statusText(day.status) }}
                </n-tag>
                <n-space size="small">
                  <n-button
                    v-if="day.id && day.status !== 'completed'"
                    size="tiny"
                    type="primary"
                    :loading="dayStatusSavingMap[day.id]?.saving"
                    @click="markDayCompleted(day)"
                  >
                    {{
                      dayStatusSavingMap[day.id]?.saving
                        ? '正在标记…'
                        : '标记为已完成'
                    }}
                  </n-button>
                  <n-button
                    v-if="day.id"
                    size="tiny"
                    tertiary
                    :loading="dayRefineMap[day.id]?.loading"
                    @click="refineDayTasks(day)"
                  >
                    {{
                      dayRefineMap[day.id]?.loading
                        ? '正在细化…'
                        : '细化今日任务'
                    }}
                  </n-button>
                  <n-button
                    v-if="day.id && day.status !== 'completed'"
                    size="tiny"
                    quaternary
                    type="warning"
                    :loading="dayReplanMap[day.id]?.loading"
                    @click="replanDay(day)"
                  >
                    {{
                      dayReplanMap[day.id]?.loading
                        ? '正在顺延…'
                        : '顺延并重排'
                    }}
                  </n-button>
                </n-space>
              </div>
            </div>

            <p v-if="dayStatusSavingMap[day.id]?.error" class="error-text">
              {{ dayStatusSavingMap[day.id].error }}
            </p>
            <p v-if="dayRefineMap[day.id]?.error" class="error-text">
              {{ dayRefineMap[day.id].error }}
            </p>
            <p v-if="dayReplanMap[day.id]?.error" class="error-text">
              {{ dayReplanMap[day.id].error }}
            </p>

            <div class="day-body">
              <div class="day-tasks">
                <div class="sub-section-title">学习任务</div>
                <p v-if="getDayGoal(day)" class="day-goal-text">
                  今日目标：{{ getDayGoal(day) }}
                </p>
                <p v-if="getDayReviewOf(day).length" class="helper-text day-review-text">
                  关联复习：{{ getDayReviewOf(day).join(' / ') }}
                </p>
                <ul class="tasks-list">
                  <li v-for="(task, tIdx) in visibleTasks(day)" :key="tIdx">
                    {{ task }}
                  </li>
                </ul>
                <n-button
                  v-if="day.tasks && day.tasks.length > 3"
                  text
                  size="tiny"
                  @click="toggleTasks(day)"
                >
                  {{ taskExpandState[day.id] ? '收起任务' : `展开剩余 ${day.tasks.length - 3} 项` }}
                </n-button>
              </div>

              <div class="day-actions" v-if="day.id">
                <div class="sub-section-title">资源与练习</div>
                <n-space size="small" wrap>
                  <n-button
                    size="tiny"
                    quaternary
                    type="primary"
                    :loading="dayResourcesMap[day.id]?.loading"
                    @click="loadDayResources(day)"
                  >
                    {{
                      dayResourcesMap[day.id]?.loading
                        ? '正在为当日加载推荐资源…'
                        : '查看当日推荐资源'
                    }}
                  </n-button>
                  <n-button
                    size="tiny"
                    quaternary
                    :loading="dayExercisesMap[day.id]?.loading"
                    @click="loadDayExercises(day)"
                  >
                    {{
                      dayExercisesMap[day.id]?.loading
                        ? '正在为当日生成练习题…'
                        : '生成 / 查看当日练习题'
                    }}
                  </n-button>
                </n-space>

                <p v-if="dayResourcesMap[day.id]?.error" class="error-text">
                  {{ dayResourcesMap[day.id].error }}
                </p>

                <div
                  v-else-if="
                    dayResourcesMap[day.id]?.loadedOnce &&
                    !dayResourcesMap[day.id]?.loading &&
                    dayResourcesMap[day.id]?.items &&
                    dayResourcesMap[day.id].items.length === 0
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
                    <n-button size="small" secondary @click="loadDayResources(day)">
                      重新获取推荐
                    </n-button>
                  </div>
                </div>
                <ul
                  v-if="
                    dayResourcesMap[day.id]?.items &&
                    dayResourcesMap[day.id].items.length
                  "
                  class="resources-list"
                >
                  <li
                    v-for="(res, rIdx) in dayResourcesMap[day.id].items"
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
                    <ResourceEvidenceList
                      :evidence="res.evidence"
                      :evidence-status="res.evidenceStatus"
                      :confidence="res.confidence"
                    />
                    <div v-if="res.id" class="resource-feedback-row">
                      <span class="resource-feedback-label">你的感觉：</span>
                      <n-radio-group
                        size="small"
                        :disabled="getFeedbackState(res.id).loading"
                        :value="getFeedbackState(res.id).value"
                        @update:value="(val) => handleFeedbackChange(res, val)"
                      >
                        <n-radio value="helpful">👍 有帮助</n-radio>
                        <n-radio value="invalid">👎 不相关 / 无效</n-radio>
                      </n-radio-group>
                      <n-tag v-if="getFeedbackState(res.id).value" size="small" type="info">
                        已反馈：{{ formatResourceFeedbackLabel(getFeedbackState(res.id).value) }}
                      </n-tag>
                    </div>
                  </li>
                </ul>

                <p v-if="dayExercisesMap[day.id]?.error" class="error-text">
                  {{ dayExercisesMap[day.id].error }}
                </p>

                <p
                  v-else-if="
                    dayExercisesMap[day.id]?.loadedOnce &&
                    !dayExercisesMap[day.id]?.loading &&
                    dayExercisesMap[day.id]?.items &&
                    dayExercisesMap[day.id].items.length === 0
                  "
                  class="helper-text"
                >
                  当前暂未生成练习题，可以稍后重试或更换学习主题。
                </p>

                <ul
                  v-if="
                    dayExercisesMap[day.id]?.items &&
                    dayExercisesMap[day.id].items.length
                  "
                  class="exercise-list"
                >
                  <li
                    v-for="(q, qIdx) in dayExercisesMap[day.id].items"
                    :key="qIdx"
                    class="exercise-item"
                  >
                    <div class="exercise-question">
                      练习 {{ qIdx + 1 }}：{{ q.question }}
                    </div>
                    <div v-if="q.difficulty || q.skillFocus || q.adaptation?.applied" class="exercise-meta">
                      <span v-if="q.difficulty">难度：{{ q.difficulty }}</span>
                      <span v-if="q.skillFocus">考察点：{{ q.skillFocus }}</span>
                      <span v-if="q.adaptation?.applied">掌握度自适应题型</span>
                    </div>
                    <textarea
                      class="exercise-answer-input"
                      rows="3"
                      placeholder="写下你的答案，提交后会先由 AI 评测，再自动保存到服务端。"
                      v-model="dayExercisesMap[day.id].answers[qIdx]"
                    ></textarea>
                    <div class="exercise-actions-row">
                      <n-button
                        size="tiny"
                        quaternary
                        :loading="isExerciseSubmitting(day.id, qIdx)"
                        @click="saveExerciseForDay(day, qIdx)"
                      >
                        {{
                          isExerciseSubmitting(day.id, qIdx)
                            ? '正在评测并保存…'
                            : '评测并保存作答'
                        }}
                      </n-button>
                    </div>
                    <div v-if="getExerciseResult(day.id, qIdx)" class="exercise-feedback-card">
                      <div class="exercise-feedback-header">
                        <n-tag
                          v-if="getExerciseResult(day.id, qIdx)?.score != null"
                          size="small"
                          :type="scoreTagType(getExerciseResult(day.id, qIdx)?.score)"
                        >
                          得分 {{ getExerciseResult(day.id, qIdx)?.score }}
                        </n-tag>
                        <span
                          v-if="getExerciseResult(day.id, qIdx)?.mistakeType"
                          class="exercise-feedback-type"
                        >
                          {{ formatMistakeType(getExerciseResult(day.id, qIdx)?.mistakeType) }}
                        </span>
                      </div>
                      <div v-if="getExerciseResult(day.id, qIdx)?.feedback" class="exercise-feedback-text">
                        {{ getExerciseResult(day.id, qIdx)?.feedback }}
                      </div>
                      <div
                        v-if="getExerciseResult(day.id, qIdx)?.nextRecommendation"
                        class="exercise-feedback-next"
                      >
                        下一步建议：{{ getExerciseResult(day.id, qIdx)?.nextRecommendation }}
                      </div>
                    </div>
                    <div
                      v-else-if="dayExercisesMap[day.id]?.submittingIndex === qIdx"
                      class="helper-text"
                    >
                      正在生成本题评测反馈，请稍候…
                    </div>
                    <div
                      v-if="dayExercisesMap[day.id]?.lastSavedAt && dayExercisesMap[day.id]?.lastSubmittedIndex === qIdx && dayExercisesMap[day.id]?.submittingIndex !== qIdx"
                      class="helper-text exercise-saved-hint"
                    >
                      本题最近一次保存时间：{{ dayExercisesMap[day.id].lastSavedAt }}
                    </div>
                    <div
                      v-if="dayExercisesMap[day.id]?.saveError && dayExercisesMap[day.id]?.lastSubmittedIndex === qIdx && dayExercisesMap[day.id]?.submittingIndex == null"
                      class="error-text"
                    >
                      {{ dayExercisesMap[day.id].saveError }}
                    </div>
                    <div
                      v-if="dayExercisesMap[day.id]?.saveSuccessMessage && dayExercisesMap[day.id]?.lastSubmittedIndex === qIdx && dayExercisesMap[day.id]?.submittingIndex == null"
                      class="helper-text exercise-save-success"
                    >
                      {{ dayExercisesMap[day.id].saveSuccessMessage }}
                    </div>
                    <details class="exercise-details">
                      <summary>查看参考答案与讲解</summary>
                      <div class="exercise-answer-ref">
                        <strong>参考答案：</strong>{{ q.answer }}
                      </div>
                      <div v-if="q.explanation" class="exercise-explanation">
                        <strong>讲解：</strong>{{ q.explanation }}
                      </div>
                    </details>
                  </li>
                </ul>
              </div>
            </div>
          </n-card>
        </n-space>
      </div>
    </div>
  </n-card>
</template>

<script setup>
import { computed, onMounted, reactive, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  getPlanProgress,
  getResourcesByPlan,
  replanPlan
} from '../api/plan';
import { usePlanStudyActions } from '../composables/usePlanStudyActions';
import { describeAdaptation } from '../utils/adaptation';
import { useResourceFeedback } from '../composables/useResourceFeedback';
import { useAuthStore } from '../store/auth';
import { buildResourceQualityParts } from '../utils/resource';
import ResourceEvidenceList from './ResourceEvidenceList.vue';

const props = defineProps({
  plan: {
    type: Object,
    default: null
  }
});

const router = useRouter();

function pickValue(source, camelKey, snakeKey) {
  if (!source) return null;
  return source[camelKey] ?? source[snakeKey] ?? null;
}

function pickList(source, camelKey, snakeKey) {
  const value = pickValue(source, camelKey, snakeKey);
  return Array.isArray(value) ? value : [];
}

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

const taskExpandState = reactive({});
const dayReplanMap = reactive({});

const progress = reactive({
  loaded: false,
  loading: false,
  error: '',
  totalDays: 0,
  completedDays: 0,
  completionRate: 0
});

const planResources = reactive({
  loading: false,
  error: '',
  items: [],
  loadedOnce: false
});
const adaptation = computed(() => pickValue(props.plan, 'adaptation', 'adaptation'));
const adaptationDescription = computed(() => describeAdaptation(adaptation.value));
const traceId = computed(() => pickValue(props.plan, 'traceId', 'trace_id') || '');
const goalBlueprint = computed(() => pickValue(props.plan, 'goalBlueprint', 'goal_blueprint'));
const goalTopics = computed(() => pickList(goalBlueprint.value, 'topics', 'topics'));
const goalMilestones = computed(() => pickList(goalBlueprint.value, 'milestones', 'milestones'));
const planPhases = computed(() => pickList(props.plan, 'phases', 'phases'));
const planWeeks = computed(() => pickList(props.plan, 'weeks', 'weeks'));
const validationReport = computed(() => pickValue(props.plan, 'validationReport', 'validation_report'));
const validationIssues = computed(() => pickList(validationReport.value, 'issues', 'issues'));
const validationWarnings = computed(() => pickList(validationReport.value, 'warnings', 'warnings'));
const validationSuggestedFixes = computed(() => pickList(validationReport.value, 'suggestedFixes', 'suggested_fixes'));
const traceIdShort = computed(() => {
  if (!traceId.value) return '';
  return traceId.value.length > 14
    ? `${traceId.value.slice(0, 8)}...${traceId.value.slice(-4)}`
    : traceId.value;
});
const agentLogHref = computed(() => {
  if (!traceId.value) return '';
  return `/debug/agent-logs?traceId=${encodeURIComponent(traceId.value)}`;
});
const planDays = computed(() => pickList(props.plan, 'days', 'days'));
const totalPlanDays = computed(() => planDays.value.length);
const totalCompletedDays = computed(() =>
  planDays.value.filter((day) => String(day.status || '').toLowerCase() === 'completed').length
);
const nextPendingDay = computed(() =>
  planDays.value.find((day) => String(day.status || '').toLowerCase() !== 'completed') || null
);
const planSnapshotCards = computed(() => {
  const coverage = validationReport.value
    ? validationReport.value.coverageScore ?? validationReport.value.coverage_score ?? '--'
    : '--';
  const loadBalance = validationReport.value
    ? validationReport.value.loadBalanceScore ?? validationReport.value.load_balance_score ?? '--'
    : '--';
  return [
    {
      label: '计划跨度',
      value: `${totalPlanDays.value} 天`,
      meta: totalCompletedDays.value > 0 ? `已完成 ${totalCompletedDays.value} 天` : '刚生成，适合立即开始'
    },
    {
      label: '阶段结构',
      value: `${planPhases.value.length || 0} 个阶段`,
      meta: `${planWeeks.value.length || 0} 周节奏已展开`
    },
    {
      label: '主题覆盖',
      value: `${coverage}`,
      meta: goalTopics.value.length ? `${goalTopics.value.length} 个核心主题` : '使用目标蓝图校验'
    },
    {
      label: '负载均衡',
      value: `${loadBalance}`,
      meta: progress.loaded ? `当前完成度 ${progress.completionRate}%` : '按每天投入自动平衡'
    }
  ];
});

function formatDate(dateStr) {
  if (!dateStr) return '';
  return dateStr;
}

function statusText(status) {
  if (!status) return '未开始';
  const map = {
    not_started: '未开始',
    in_progress: '进行中',
    completed: '已完成',
    delayed: '延迟'
  };
  return map[status] || status;
}

function statusTagType(status) {
  if (!status) return 'default';
  const map = {
    not_started: 'default',
    in_progress: 'info',
    completed: 'success',
    delayed: 'warning'
  };
  return map[status] || 'default';
}

function formatTaskType(taskType) {
  if (!taskType) return '';
  const map = {
    learn: '新知学习',
    practice: '练习巩固',
    review: '复盘回顾',
    build: '项目实战',
    exercise_driven: '题目驱动'
  };
  return map[taskType] || taskType;
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

function validationScoreTagType(score) {
  if (score == null) return 'default';
  if (score >= 80) return 'success';
  if (score >= 60) return 'warning';
  return 'error';
}

function validationIssueTagType(severity) {
  if (severity === 'error') return 'error';
  if (severity === 'warning') return 'warning';
  return 'default';
}

function getPhaseTitleById(phaseId) {
  if (!phaseId) return '';
  const phase = planPhases.value.find(
    (item) => pickValue(item, 'phaseId', 'phase_id') === phaseId
  );
  return phase ? phase.title : '';
}

function getPhaseFocusTopics(phase) {
  return pickList(phase, 'focusTopics', 'focus_topics');
}

function getWeekFocusTopics(week) {
  return pickList(week, 'focusTopics', 'focus_topics');
}

function getDayWeekIndex(day) {
  return pickValue(day, 'weekIndex', 'week_index');
}

function getDayPhaseId(day) {
  return pickValue(day, 'phaseId', 'phase_id');
}

function getDayGoal(day) {
  return pickValue(day, 'goal', 'goal');
}

function getDayEstimatedMinutes(day) {
  return pickValue(day, 'estimatedMinutes', 'estimated_minutes');
}

function getDayTaskType(day) {
  return pickValue(day, 'taskType', 'task_type');
}

function getDayDifficulty(day) {
  return pickValue(day, 'difficulty', 'difficulty');
}

function getDayReviewOf(day) {
  return pickList(day, 'reviewOf', 'review_of');
}

function dayMetaParts(day) {
  const parts = [];
  const weekIndex = getDayWeekIndex(day);
  const phaseId = getDayPhaseId(day);
  const taskType = getDayTaskType(day);
  const estimatedMinutes = getDayEstimatedMinutes(day);
  const difficulty = getDayDifficulty(day);

  if (weekIndex) {
    parts.push(`第${weekIndex}周`);
  }
  if (phaseId) {
    const phaseTitle = getPhaseTitleById(phaseId);
    parts.push(phaseTitle || phaseId);
  }
  if (taskType) {
    parts.push(formatTaskType(taskType));
  }
  if (estimatedMinutes) {
    parts.push(`约 ${estimatedMinutes} 分钟`);
  }
  if (difficulty) {
    parts.push(`难度 ${difficulty}`);
  }
  return parts;
}

async function loadPlanResources() {
  if (!props.plan) return;

  const planId = props.plan.planId || props.plan.plan_id || props.plan.id;
  if (!planId) {
    planResources.error =
      '当前计划缺少 planId，请从“历史计划”页面打开该计划后再试。';
    planResources.loadedOnce = true;
    return;
  }

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

async function loadPlanProgress() {
  if (!props.plan) return;
  const planId = props.plan.planId || props.plan.plan_id || props.plan.id;
  if (!planId) return;

  progress.loading = true;
  progress.error = '';

  try {
    const data = await getPlanProgress(planId);
    progress.totalDays = data.totalDays ?? 0;
    progress.completedDays = data.completedDays ?? 0;
    progress.completionRate = data.completionRate ?? 0;
    progress.loaded = true;
  } catch (e) {
    console.error(e);
    progress.error = '获取计划完成度失败';
  } finally {
    progress.loading = false;
  }
}

onMounted(() => {
  loadPlanProgress();
});

watch(
  () => props.plan && (props.plan.planId || props.plan.plan_id || props.plan.id),
  () => {
    loadPlanProgress();
  }
);

function resourceQualityParts(resource) {
  return buildResourceQualityParts(resource);
}

function goToResourceUpload() {
  router.push('/upload-resource');
}

function syncPlanSnapshot(targetPlan, nextPlan) {
  if (!targetPlan || !nextPlan) return;
  targetPlan.planId = nextPlan.planId ?? nextPlan.plan_id ?? targetPlan.planId;
  targetPlan.title = nextPlan.title ?? targetPlan.title;
  targetPlan.startDate = nextPlan.startDate ?? nextPlan.start_date ?? targetPlan.startDate;
  targetPlan.endDate = nextPlan.endDate ?? nextPlan.end_date ?? targetPlan.endDate;
  targetPlan.days = Array.isArray(nextPlan.days) ? nextPlan.days : [];
  targetPlan.traceId = nextPlan.traceId ?? nextPlan.trace_id ?? targetPlan.traceId;
  targetPlan.goalBlueprint = nextPlan.goalBlueprint ?? nextPlan.goal_blueprint ?? targetPlan.goalBlueprint;
  targetPlan.phases = Array.isArray(nextPlan.phases) ? nextPlan.phases : [];
  targetPlan.weeks = Array.isArray(nextPlan.weeks) ? nextPlan.weeks : [];
  targetPlan.validationReport = nextPlan.validationReport ?? nextPlan.validation_report ?? targetPlan.validationReport;
}

async function replanDay(day) {
  if (!props.plan || !day?.id) return;

  const planId = props.plan.planId || props.plan.plan_id || props.plan.id;
  const userId = currentUser.value ? currentUser.value.id : null;
  if (!planId || !userId) return;

  const reason = window.prompt(
    '请输入顺延原因（可选）',
    '今天任务未完成，顺延 1 天并重排后续计划'
  );
  if (reason === null) {
    return;
  }

  if (!dayReplanMap[day.id]) {
    dayReplanMap[day.id] = {
      loading: false,
      error: ''
    };
  }

  const state = dayReplanMap[day.id];
  state.loading = true;
  state.error = '';

  try {
    const data = await replanPlan(planId, {
      userId,
      triggerDayId: day.id,
      delayDays: 1,
      reason: reason.trim() || null
    });
    syncPlanSnapshot(props.plan, data);
    planResources.loadedOnce = false;
    planResources.items = [];
    await loadPlanProgress();
  } catch (e) {
    console.error(e);
    state.error = '顺延并重排失败，请稍后重试。';
  } finally {
    state.loading = false;
  }
}

function handleFeedbackChange(res, val) {
  if (val === 'helpful' || val === 'invalid') {
    sendResourceFeedback(res, val).catch(() => {});
  }
}

function toggleTasks(day) {
  if (!day || !day.id) return;
  taskExpandState[day.id] = !taskExpandState[day.id];
}

function visibleTasks(day) {
  if (!day.tasks) return [];
  if (taskExpandState[day.id] || day.tasks.length <= 3) return day.tasks;
  return day.tasks.slice(0, 3);
}
</script>

<style scoped>
.plan-result-card {
  margin-top: 16px;
}

.plan-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.plan-days {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.day-card {
  max-width: 420px;
}

.plan-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.plan-header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.plan-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #6b7280;
}

.plan-id {
  font-weight: 500;
}

.plan-progress {
  min-width: 200px;
}

.plan-progress-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #4b5563;
  margin-bottom: 4px;
}

.plan-progress-label {
  font-weight: 500;
}

.top-hint {
  margin: 0 0 8px;
}

.plan-v2-overview {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.overview-header {
  margin-bottom: 0;
}

.plan-v2-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.plan-resources {
  margin-top: 4px;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.4);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.section-subtitle {
  font-size: 12px;
  color: #6b7280;
}

.overview-panel {
  padding: 8px 10px;
}

.overview-meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.trace-link {
  font-size: 12px;
  color: #2563eb;
  text-decoration: none;
}

.trace-link:hover {
  text-decoration: underline;
}

.overview-summary {
  margin: 0 0 8px;
}

.resources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
  margin-top: 6px;
}

.resource-card {
  padding: 8px 10px;
}

.resource-title-row {
  margin-bottom: 4px;
}

.resources-list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.global-resources-list {
  margin-top: 6px;
}

.days-header {
  margin-bottom: 8px;
}

.day-card {
  padding: 10px 12px;
}

.day-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}

.day-header-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.day-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.day-status-tag {
  text-transform: none;
}

.day-body {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1.6fr);
  gap: 12px;
  margin-top: 6px;
}

.day-tasks,
.day-actions {
  min-width: 0;
}

.sub-section-title {
  font-size: 13px;
  font-weight: 500;
  color: #4b5563;
  margin-bottom: 4px;
}

.compact-title {
  margin-bottom: 6px;
}

.resource-item {
  font-size: 13px;
  color: #374151;
}

.resource-title {
  font-weight: 600;
  color: #111827;
}

.resource-meta {
  font-size: 12px;
  color: #6b7280;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.resource-reason {
  font-size: 12px;
  color: #4b5563;
}

.resource-empty-state {
  margin-top: 8px;
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
  margin-top: 10px;
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
  max-width: 560px;
}

.resource-empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.resource-quality-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.resource-feedback-row {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.resource-feedback-label {
  font-size: 12px;
  color: #6b7280;
}

.blueprint-topic-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.blueprint-topic-item {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.35);
}

.blueprint-topic-order {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 12px;
  font-weight: 700;
}

.blueprint-topic-body {
  min-width: 0;
}

.blueprint-topic-title-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.blueprint-topic-title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.blueprint-topic-meta,
.blueprint-topic-meta-row {
  font-size: 12px;
  color: #6b7280;
}

.blueprint-topic-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #374151;
}

.blueprint-topic-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.blueprint-milestone-block,
.validation-issue-block,
.validation-fix-block {
  margin-top: 10px;
}

.blueprint-milestone-list,
.validation-issue-list,
.validation-fix-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #374151;
}

.validation-status-row {
  margin-bottom: 8px;
}

.validation-score-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.validation-score-item {
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.35);
}

.validation-score-label {
  margin-bottom: 4px;
  font-size: 12px;
  color: #6b7280;
}

.phase-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.phase-card,
.week-item {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: #f8fafc;
}

.phase-card-title-row,
.week-item-header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: flex-start;
}

.phase-card-title,
.week-item-title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.phase-card-goal,
.phase-card-meta,
.phase-card-outcome,
.week-item-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #4b5563;
}

.week-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.day-submeta {
  font-size: 12px;
  color: #6b7280;
}

.day-goal-text,
.day-review-text {
  margin: 0 0 6px;
  font-size: 12px;
}

.exercise-list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.exercise-item {
  padding: 8px 10px;
  border-radius: 8px;
  background-color: #f9fafb;
  border: 1px solid rgba(148, 163, 184, 0.5);
}

.exercise-question {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 4px;
}

.exercise-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 6px;
}

.exercise-answer-input {
  width: 100%;
  margin-top: 4px;
  font-size: 13px;
  padding: 4px 6px;
  border-radius: 6px;
  border: 1px solid rgba(148, 163, 184, 0.8);
  resize: vertical;
}

.exercise-feedback-card {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #eff6ff;
  border: 1px solid rgba(96, 165, 250, 0.35);
}

.exercise-feedback-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.exercise-feedback-type {
  font-size: 12px;
  color: #1d4ed8;
  font-weight: 500;
}

.exercise-feedback-text,
.exercise-feedback-next {
  font-size: 12px;
  color: #1f2937;
  white-space: pre-wrap;
}

.exercise-feedback-next {
  margin-top: 4px;
}

.exercise-saved-hint,
.exercise-save-success {
  margin-top: 6px;
}

.exercise-details {
  margin-top: 8px;
  font-size: 12px;
}

.exercise-answer-ref,
.exercise-explanation {
  margin-top: 4px;
  color: #374151;
}

@media (max-width: 960px) {
  .plan-header,
  .day-body,
  .plan-v2-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .plan-header {
    flex-direction: column;
  }

  .day-body {
    display: flex;
    flex-direction: column;
  }
}

.plan-result-card {
  margin-top: 8px;
  border-radius: 24px;
  overflow: hidden;
}

.plan-header {
  align-items: stretch;
}

.plan-header-left {
  gap: 8px;
}

.plan-title {
  font-size: clamp(24px, 2.2vw, 32px);
  line-height: 1.1;
  color: #0f2940;
}

.plan-meta {
  gap: 10px;
}

.plan-id,
.plan-date {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f3f7f8;
  border: 1px solid rgba(15, 41, 64, 0.08);
}

.plan-progress {
  min-width: 260px;
  padding: 16px;
  border-radius: 20px;
  background: linear-gradient(180deg, #f7fbfd, #ecf5f3);
  border: 1px solid rgba(15, 41, 64, 0.08);
}

.top-hint {
  margin: 4px 0 0;
}

.adaptation-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin: 12px 0 4px;
  border: 1px solid rgba(24, 160, 88, 0.22);
  border-radius: 10px;
  background: rgba(24, 160, 88, 0.06);
  color: #315347;
}

.plan-snapshot-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0 10px;
}

.plan-snapshot-card {
  padding: 16px;
  border-radius: 20px;
  background: linear-gradient(180deg, #ffffff, #f6fafb);
  border: 1px solid rgba(15, 41, 64, 0.08);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.plan-snapshot-label {
  font-size: 12px;
  color: #6e8090;
}

.plan-snapshot-value {
  margin-top: 5px;
  font-size: 24px;
  font-weight: 700;
  color: #102235;
}

.plan-snapshot-meta {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #627686;
}

.plan-next-step {
  margin-bottom: 16px;
  padding: 16px 18px;
  border-radius: 22px;
  background: linear-gradient(135deg, #173850, #2a6760 130%);
  color: #f8fafc;
}

.plan-next-step-label {
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(241, 248, 251, 0.74);
}

.plan-next-step-value {
  margin-top: 8px;
  font-size: 20px;
  font-weight: 700;
  color: #ffffff;
}

.plan-next-step-meta {
  margin-top: 6px;
  font-size: 12px;
  color: rgba(241, 248, 251, 0.82);
}

.plan-v2-overview {
  gap: 14px;
}

.overview-panel {
  border-radius: 20px;
  background: linear-gradient(180deg, #ffffff, #fbfcfd);
}

.day-card {
  max-width: none;
  border-radius: 20px;
  border: 1px solid rgba(15, 41, 64, 0.08);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.day-header {
  gap: 14px;
  margin-bottom: 10px;
}

.day-title {
  font-size: 18px;
  line-height: 1.35;
  color: #102235;
}

.day-submeta {
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .plan-snapshot-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .plan-progress,
  .plan-snapshot-grid {
    width: 100%;
  }

  .plan-snapshot-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .plan-next-step-value {
    font-size: 18px;
  }
}
</style>











