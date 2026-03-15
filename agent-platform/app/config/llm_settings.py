"""
本文件用于集中配置大模型（LLM）相关参数。

⚠️ 安全提醒：
- 这里通常会写入私密的 API Key，请不要把包含真实 Key 的文件提交到公共仓库。
- 建议：在 Git 中只提交一个不含真实 Key 的模板文件（例如 llm_settings.example.py），
  然后在本地复制一份 llm_settings.py 自己填写。

当前项目的 core/llm.py 和 PlanAgent 会优先从这里读取配置，
若某项未配置，则退回到读取环境变量。
"""

# LLM 接口地址（DeepSeek 兼容 OpenAI 协议）
LLM_API_BASE: str | None = None

# 你的 DeepSeek API Key（在本地填写真实值）
# 例如： "sk-xxxx..."
LLM_API_KEY: str | None = None

# 默认使用的模型名称
# DeepSeek 一般是 "deepseek-chat"
LLM_API_MODEL: str | None = "deepseek-chat"

# 是否启用 PlanAgent 使用 LLM 直接生成学习计划
# - True：PlanAgent 会优先调用 LLM 生成多天计划；
# - False：仅使用规则 + GoalAgent 主题生成 3 天示例计划。
ENABLE_LLM_PLAN: bool = True


