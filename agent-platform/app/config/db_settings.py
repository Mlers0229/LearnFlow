"""
本文件用于集中配置 Agent 平台使用的数据库连接信息。

⚠️ 建议：
- 若项目托管在公共仓库中，请不要提交包含真实密码的版本；
- 可以只提交一个 db_settings.example.py 模板，本地复制为 db_settings.py 并填写真实信息。

当前默认使用 Postgres 作为学习资源库（resource_bank）存储。
"""

# 方式一：直接提供完整的 SQLAlchemy 数据库 URL（推荐）
# 例如： "postgresql+psycopg2://learnflow_user:your_password@localhost:5432/learnflow"
DATABASE_URL: str | None = None

# 方式二：按字段拆分（如果 DATABASE_URL 为空且以下字段都存在，会自动拼接）
DB_HOST: str | None = "localhost"
DB_PORT: int | None = 5432
DB_NAME: str | None = "learnflow"
DB_USER: str | None = "learnflow_user"
DB_PASSWORD: str | None = "0229"
DB_DRIVER: str | None = "postgresql+psycopg2"


