[Unit]
Description=LearnFlow Agent Platform
After=network.target

[Service]
WorkingDirectory=__DEPLOY_ROOT__/agent-platform
EnvironmentFile=/etc/learnflow/agent.env
ExecStart=__DEPLOY_ROOT__/agent-platform/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port __AGENT_PORT__
Restart=always
RestartSec=5
User=__APP_USER__
Group=__APP_GROUP__

[Install]
WantedBy=multi-user.target
