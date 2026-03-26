"""
Agent 平台本地数据库配置示例。

- 复制为 db_settings_local.py 后填写本机真实配置；
- 该文件已加入 .gitignore，不会被提交到仓库。
"""

DATABASE_URL = "postgresql+psycopg2://learnflow_user:YOUR_PASSWORD@localhost:5432/learnflow"

# 如果你更习惯按字段拆分，也可以改为：
# DATABASE_URL = None
# DB_HOST = "localhost"
# DB_PORT = 5432
# DB_NAME = "learnflow"
# DB_USER = "learnflow_user"
# DB_PASSWORD = "YOUR_PASSWORD"
# DB_DRIVER = "postgresql+psycopg2"
