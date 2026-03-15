[Unit]
Description=LearnFlow Backend
After=network.target learnflow-agent.service

[Service]
WorkingDirectory=__DEPLOY_ROOT__/backend
EnvironmentFile=/etc/learnflow/backend.env
ExecStart=/usr/bin/java -jar __DEPLOY_ROOT__/backend/target/__BACKEND_JAR__
Restart=always
RestartSec=5
User=__APP_USER__
Group=__APP_GROUP__

[Install]
WantedBy=multi-user.target
