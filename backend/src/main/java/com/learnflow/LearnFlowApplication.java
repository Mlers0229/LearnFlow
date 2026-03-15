package com.learnflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LearnFlow 后端应用入口。
 *
 * 目前只提供最基础的启动能力和简单的 REST 接口骨架，
 * 后续会逐步接入数据库（Postgres）和 AI Agent 平台（FastAPI）。
 */
@SpringBootApplication
public class LearnFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearnFlowApplication.class, args);
    }
}


