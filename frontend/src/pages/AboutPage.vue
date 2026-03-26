<template>
  <div class="about-page">
    <section class="about-hero">
      <div class="hero-copy">
        <div class="about-kicker">系统总览</div>
        <h1 class="title about-title">关于 LearnFlow</h1>
        <p class="subtitle about-subtitle">
          LearnFlow 已经从最初的计划生成页面演进成一套更完整的学习工作台：用户侧负责计划生成、历史复盘、练习沉淀、AI 对话与资源上传，
          管理侧负责资源治理、模型配置、运行监控与数据总览。
        </p>

        <div class="hero-tags">
          <span v-for="tag in heroTags" :key="tag" class="hero-tag">{{ tag }}</span>
        </div>
      </div>

      <div class="hero-focus-card">
        <div class="hero-focus-label">当前版本焦点</div>
        <div class="hero-focus-title">从“生成计划”升级为“学习闭环工作台”</div>
        <div class="hero-focus-desc">
          现在系统不仅能生成计划，还能把资源推荐、练习评测、历史回顾、AI 对话、管理端模型策略和资源治理串成一条可持续迭代的学习链路。
        </div>
        <div class="hero-focus-metrics">
          <div class="focus-chip">
            <span>用户工作台</span>
            <strong>已成型</strong>
          </div>
          <div class="focus-chip">
            <span>管理端联动</span>
            <strong>已接线</strong>
          </div>
        </div>
      </div>
    </section>

    <section class="stats-grid">
      <n-card
        v-for="item in summaryStats"
        :key="item.label"
        size="small"
        class="summary-card"
        :bordered="false"
      >
        <div class="summary-label">{{ item.label }}</div>
        <div class="summary-value">{{ item.value }}</div>
        <div class="summary-desc">{{ item.desc }}</div>
      </n-card>
    </section>

    <section class="capability-grid">
      <n-card
        v-for="block in capabilityBlocks"
        :key="block.title"
        size="small"
        class="about-panel capability-card"
      >
        <template #header>
          <div class="panel-header">
            <div>
              <div class="panel-kicker">{{ block.kicker }}</div>
              <div class="panel-title">{{ block.title }}</div>
            </div>
          </div>
        </template>

        <p class="panel-desc">{{ block.desc }}</p>
        <ul class="panel-list">
          <li v-for="point in block.points" :key="point">{{ point }}</li>
        </ul>
      </n-card>
    </section>

    <section class="double-grid">
      <n-card size="small" class="about-panel">
        <template #header>
          <div class="panel-header">
            <div>
              <div class="panel-kicker">页面地图</div>
              <div class="panel-title">当前已经形成的双端结构</div>
            </div>
          </div>
        </template>

        <div class="page-columns">
          <div class="page-column">
            <div class="page-column-title">用户端</div>
            <div
              v-for="item in userPages"
              :key="item.title"
              class="page-item"
            >
              <div class="page-item-title">{{ item.title }}</div>
              <div class="page-item-desc">{{ item.desc }}</div>
            </div>
          </div>

          <div class="page-column">
            <div class="page-column-title">管理端</div>
            <div
              v-for="item in adminPages"
              :key="item.title"
              class="page-item"
            >
              <div class="page-item-title">{{ item.title }}</div>
              <div class="page-item-desc">{{ item.desc }}</div>
            </div>
          </div>
        </div>
      </n-card>

      <n-card size="small" class="about-panel">
        <template #header>
          <div class="panel-header">
            <div>
              <div class="panel-kicker">能力链路</div>
              <div class="panel-title">一条更完整的学习闭环</div>
            </div>
          </div>
        </template>

        <n-timeline size="small" class="system-timeline">
          <n-timeline-item
            v-for="item in flowTimeline"
            :key="item.title"
            :title="item.title"
            :content="item.content"
          />
        </n-timeline>
      </n-card>
    </section>

    <section class="double-grid">
      <n-card size="small" class="about-panel">
        <template #header>
          <div class="panel-header">
            <div>
              <div class="panel-kicker">最近变化</div>
              <div class="panel-title">这一版系统更新了什么</div>
            </div>
          </div>
        </template>

        <div class="change-list">
          <div
            v-for="item in recentChanges"
            :key="item.title"
            class="change-item"
          >
            <div class="change-title">{{ item.title }}</div>
            <div class="change-desc">{{ item.desc }}</div>
          </div>
        </div>
      </n-card>

      <n-card size="small" class="about-panel">
        <template #header>
          <div class="panel-header">
            <div>
              <div class="panel-kicker">系统架构</div>
              <div class="panel-title">当前技术分层</div>
            </div>
          </div>
        </template>

        <div class="arch-stack">
          <div
            v-for="layer in architectureLayers"
            :key="layer.name"
            class="arch-layer"
          >
            <div class="arch-layer-name">{{ layer.name }}</div>
            <div class="arch-layer-desc">{{ layer.desc }}</div>
            <div class="arch-layer-tags">
              <span v-for="tag in layer.tags" :key="tag" class="arch-tag">{{ tag }}</span>
            </div>
          </div>
        </div>
      </n-card>
    </section>

    <section class="about-footer">
      <div class="footer-note">
        这页现在可以直接作为系统简介页使用，配合历史计划页、练习回顾页和管理端 Dashboard 一起展示当前版本的完整能力面。
      </div>
    </section>
  </div>
</template>

<script setup>
const heroTags = [
  '学习计划生成',
  '历史复盘工作台',
  '练习评测沉淀',
  'AI 对话辅学',
  '资源上传与治理',
  '管理端模型配置'
];

const summaryStats = [
  { label: '系统形态', value: '双端协同', desc: '用户工作台 + 管理端控制台已形成基础闭环' },
  { label: '技术分层', value: '3 层', desc: 'Vue 前端、Spring Boot 后端、FastAPI Agent 平台' },
  { label: 'AI 能力链', value: '多 Agent', desc: '目标拆解、计划生成、资源推荐、练习生成与评测' },
  { label: '当前重点', value: '可运营化', desc: '持续完善资源治理、模型策略与运营看板' }
];

const capabilityBlocks = [
  {
    kicker: '用户工作台',
    title: '从生成计划到执行复盘',
    desc: '用户端已经不是单一表单页，而是一套连续学习界面。',
    points: [
      '生成学习计划页支持输入目标、时间与学习偏好，形成结构化学习计划',
      '历史计划页升级为复盘工作台，支持计划切换、日程索引、任务细化、资源和练习联动',
      '练习回顾页可按计划与待复习状态筛选，并支持删除单条记录或清空某日记录',
      'AI 对话页保留当前模型展示，围绕学习场景做持续辅学'
    ]
  },
  {
    kicker: '资源闭环',
    title: '推荐、上传、反馈开始联通',
    desc: '资源系统已经从“展示列表”升级为“推荐与治理”并行。',
    points: [
      '推荐结果会结合领域、标签、难度与反馈做更贴近学习主题的筛选',
      '资源推荐空态已经加入“去上传资源”入口，补齐冷启动体验',
      '上传资源页与我的上传记录、审核状态查询已经接通',
      '用户对资源可做“有帮助 / 不相关”反馈，为后续推荐优化提供依据'
    ]
  },
  {
    kicker: '管理端',
    title: '从静态后台走向真实数据联动',
    desc: '管理端目前已经具备基础运营和策略配置能力。',
    points: [
      'Dashboard 已接真实聚合数据，能看计划、资源、用户与 Agent 运行概况',
      '资源管理页与上传审核流更统一，方便做内容治理',
      '模型配置页已接入后端代理接口，可集中管理模型策略',
      '普通用户在分级前不开放模型选择，模型策略主要收敛在管理端'
    ]
  },
  {
    kicker: '系统基础能力',
    title: '多 Agent 与后端代理链路更稳定',
    desc: '这部分是支撑整个工作台持续运行的底层能力。',
    points: [
      '后端已修复计划生成链路中的 JSON 解析问题，避免健康 Agent 被错误回退到本地兜底方案',
      '模型列表与管理端配置通过后端统一代理，降低前端直连复杂度',
      '练习记录、资源反馈、历史计划等核心数据已经持续沉淀到系统中',
      '顶栏、页面视觉语言和中后台骨架逐步统一，系统感比初版更完整'
    ]
  }
];

const userPages = [
  { title: '生成学习计划', desc: '输入目标与约束，生成可执行学习计划。' },
  { title: '历史计划', desc: '查看历史计划、日程索引、每日任务、资源与练习。' },
  { title: '练习回顾', desc: '聚合历次练习作答、AI 评分、反馈与待复习题。' },
  { title: 'AI 对话', desc: '围绕学习目标做问答、解释与持续辅学。' },
  { title: '上传学习资源', desc: '提交资源并查询上传记录与审核状态。' }
];

const adminPages = [
  { title: 'Dashboard', desc: '展示系统核心指标、资源状态、日志概览和运行健康度。' },
  { title: '资源管理', desc: '做资源审核、编辑、上下架和内容治理。' },
  { title: '模型配置', desc: '集中管理第三方模型配置、模型列表与策略。' },
  { title: '用户管理', desc: '维护账号、角色与管理权限。' },
  { title: '日志与调试', desc: '追踪 Agent 调用日志与系统调试信息。' }
];

const flowTimeline = [
  {
    title: '1. 输入学习目标',
    content: '用户在计划生成页描述目标、周期、投入时间与学习倾向。'
  },
  {
    title: '2. Agent 生成计划',
    content: '后端调用 Agent 平台完成目标拆解、学习阶段组织与按天计划生成。'
  },
  {
    title: '3. 历史工作台执行',
    content: '用户在历史计划页切换计划、查看日程、细化任务、加载资源与练习。'
  },
  {
    title: '4. 练习记录沉淀',
    content: '系统保存用户答案、AI 评分、错因分析与下一步建议。'
  },
  {
    title: '5. 练习回顾复盘',
    content: '用户在练习回顾页回看薄弱题型、聚焦待复习内容。'
  },
  {
    title: '6. 资源反馈反哺',
    content: '用户上传资源、反馈推荐结果，管理端再做审核与治理。'
  },
  {
    title: '7. 管理端统一策略',
    content: '管理员在 Dashboard、资源管理和模型配置页中统一调优系统。'
  }
];

const recentChanges = [
  {
    title: '页面视觉语言更统一',
    desc: '历史计划页、练习回顾页、资源上传页和管理端顶栏已经逐步统一成同一套产品风格。'
  },
  {
    title: '历史计划页交互升级',
    desc: '新增双层导航、计划快速切换、记住上次查看日程位置，以及更完整的执行工作台布局。'
  },
  {
    title: '练习回顾支持清理记录',
    desc: '现在可以删除单条练习记录，也可以清空某个学习日下的全部练习记录。'
  },
  {
    title: '资源流转更完整',
    desc: '上传资源页、我的上传记录、审核状态查询与推荐空态入口已经接通。'
  },
  {
    title: '模型配置已收敛到管理端',
    desc: '模型策略不再分散暴露给普通用户，管理端负责统一配置并自动获取可用模型列表。'
  },
  {
    title: 'Dashboard 接入真实数据',
    desc: '管理端首页开始展示后端真实聚合数据，形成可持续使用的动态总览页。'
  }
];

const architectureLayers = [
  {
    name: '前端工作台',
    desc: '基于 Vue 3 + Vite + Naive UI，承载用户端和管理端页面、交互与视觉体系。',
    tags: ['Vue 3', 'Vite', 'Naive UI', '双端路由']
  },
  {
    name: '业务后端',
    desc: '基于 Spring Boot 提供计划、资源、练习、模型配置代理、Dashboard 聚合等接口。',
    tags: ['Spring Boot', 'JPA', 'REST API', '代理聚合']
  },
  {
    name: 'Agent 平台',
    desc: '基于 FastAPI 承接多 Agent 能力，负责学习目标理解、计划生成、资源推荐与辅学能力。',
    tags: ['FastAPI', 'GoalAgent', 'PlanAgent', 'TutorAgent']
  }
];
</script>

<style scoped>
.about-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.about-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(320px, 0.7fr);
  gap: 16px;
  align-items: stretch;
  padding: 24px;
  border-radius: 28px;
  border: 1px solid rgba(17, 42, 59, 0.08);
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.86), transparent 34%),
    linear-gradient(135deg, #f9fcfc, #f2f7f4 58%, #eef4f8);
}

.about-kicker,
.panel-kicker {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #6f8191;
}

.about-title {
  margin-top: 8px;
  color: #102235;
}

.about-subtitle {
  margin-bottom: 0;
  max-width: 860px;
  line-height: 1.8;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.18);
  color: #244356;
  font-size: 12px;
  font-weight: 600;
}

.hero-focus-card {
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(135deg, #16374f, #2f6960 125%);
  color: #f8fafc;
  box-shadow: 0 16px 32px rgba(15, 41, 64, 0.16);
}

.hero-focus-label {
  font-size: 12px;
  color: rgba(243, 249, 251, 0.72);
}

.hero-focus-title {
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.3;
  font-weight: 700;
}

.hero-focus-desc {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.8;
  color: rgba(243, 249, 251, 0.86);
}

.hero-focus-metrics {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.focus-chip {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.14);
}

.focus-chip span {
  display: block;
  font-size: 12px;
  color: rgba(243, 249, 251, 0.74);
}

.focus-chip strong {
  display: block;
  margin-top: 6px;
  font-size: 18px;
  color: #ffffff;
}

.stats-grid,
.capability-grid,
.double-grid {
  display: grid;
  gap: 12px;
}

.stats-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.capability-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.double-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.summary-card,
.about-panel {
  border-radius: 22px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.summary-card {
  background: linear-gradient(180deg, #ffffff, #f7fbfb);
}

.summary-label {
  font-size: 12px;
  color: #6b7280;
}

.summary-value {
  margin-top: 4px;
  font-size: 28px;
  font-weight: 700;
  color: #102235;
}

.summary-desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: #7b8a97;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-title {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
  color: #102235;
}

.panel-desc {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.8;
  color: #5f7280;
}

.panel-list {
  margin: 0;
  padding-left: 18px;
  color: #334155;
  line-height: 1.85;
}

.page-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.page-column {
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: linear-gradient(180deg, #fcfdfd, #f7fafb);
}

.page-column-title {
  font-size: 15px;
  font-weight: 700;
  color: #102235;
}

.page-item + .page-item {
  margin-top: 12px;
}

.page-item-title {
  margin-top: 12px;
  font-size: 14px;
  font-weight: 700;
  color: #1f3b4d;
}

.page-item-desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: #667784;
}

.system-timeline {
  margin-top: 4px;
}

.change-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.change-item {
  padding: 14px 16px;
  border-radius: 18px;
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.76), transparent 40%),
    linear-gradient(180deg, #fdfefe, #f7fbfb);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.change-title {
  font-size: 14px;
  font-weight: 700;
  color: #16364b;
}

.change-desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.8;
  color: #667784;
}

.arch-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.arch-layer {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: linear-gradient(180deg, #fdfefe, #f7fbfb);
}

.arch-layer-name {
  font-size: 15px;
  font-weight: 700;
  color: #102235;
}

.arch-layer-desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.75;
  color: #667784;
}

.arch-layer-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.arch-tag {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: #eef4f7;
  color: #335468;
  font-size: 12px;
  font-weight: 600;
}

.about-footer {
  padding: 0 4px 6px;
}

.footer-note {
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, #fcfdfd, #f4f8f9);
  border: 1px dashed rgba(148, 163, 184, 0.42);
  color: #5d7181;
  line-height: 1.8;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .about-hero,
  .capability-grid,
  .double-grid,
  .stats-grid,
  .page-columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .about-hero {
    padding: 18px;
  }

  .hero-focus-metrics {
    flex-direction: column;
  }
}
</style>
