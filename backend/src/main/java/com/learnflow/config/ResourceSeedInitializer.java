package com.learnflow.config;

import com.learnflow.entity.ResourceBank;
import com.learnflow.repository.ResourceBankRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 在本地开发和演示环境中补充一批基础资源种子，并为旧数据回填领域。
 */
@Component
public class ResourceSeedInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ResourceSeedInitializer.class);

    private final ResourceBankRepository resourceBankRepository;

    public ResourceSeedInitializer(ResourceBankRepository resourceBankRepository) {
        this.resourceBankRepository = resourceBankRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfillDomainForExistingResources();
        seedMissingResources();
    }

    private void backfillDomainForExistingResources() {
        List<ResourceBank> all = resourceBankRepository.findAll();
        List<ResourceBank> changed = new ArrayList<>();
        for (ResourceBank resource : all) {
            String inferred = inferDomain(resource.getTitle(), resource.getTags());
            if (inferred == null || inferred.equals(resource.getDomain())) {
                continue;
            }
            resource.setDomain(inferred);
            changed.add(resource);
        }
        if (!changed.isEmpty()) {
            resourceBankRepository.saveAll(changed);
            log.info("已为 {} 条历史资源回填领域字段。", changed.size());
        }
    }

    private void seedMissingResources() {
        int createdCount = 0;
        for (SeedResource seed : defaultResources()) {
            if (resourceBankRepository.existsByTitleIgnoreCaseAndUrl(seed.title(), seed.url())) {
                continue;
            }
            ResourceBank entity = new ResourceBank();
            entity.setUploaderUsername("system");
            entity.setTitle(seed.title());
            entity.setUrl(seed.url());
            entity.setDomain(seed.domain());
            entity.setLevel(seed.level());
            entity.setDurationMinutes(seed.durationMinutes());
            entity.setTags(seed.tags());
            entity.setStatus("ACTIVE");
            resourceBankRepository.save(entity);
            createdCount++;
        }
        if (createdCount > 0) {
            log.info("已补充 {} 条基础资源种子。", createdCount);
        }
    }

    private List<SeedResource> defaultResources() {
        return List.of(
                new SeedResource(
                        "廖雪峰 Java 教程",
                        "https://www.liaoxuefeng.com/wiki/1252599548343744",
                        "java",
                        "beginner",
                        180,
                        "java,backend,beginner,docs"
                ),
                new SeedResource(
                        "菜鸟教程 Java 教程",
                        "https://www.runoob.com/java/java-tutorial.html",
                        "java",
                        "beginner",
                        120,
                        "java,backend,basic,runoob"
                ),
                new SeedResource(
                        "Spring Boot 参考文档",
                        "https://spring.io/projects/spring-boot",
                        "java",
                        "intermediate",
                        90,
                        "springboot,java,backend,official,docs"
                ),
                new SeedResource(
                        "PostgreSQL 教程",
                        "https://www.runoob.com/postgresql/postgresql-tutorial.html",
                        "database",
                        "beginner",
                        90,
                        "postgres,sql,database,docs"
                ),
                new SeedResource(
                        "MySQL 教程",
                        "https://www.runoob.com/mysql/mysql-tutorial.html",
                        "database",
                        "beginner",
                        90,
                        "mysql,sql,database,docs"
                ),
                new SeedResource(
                        "MyBatis 官方文档",
                        "https://mybatis.org/mybatis-3/",
                        "database",
                        "intermediate",
                        80,
                        "mybatis,mapper,sql,docs,official"
                ),
                new SeedResource(
                        "英语四级词汇与真题技巧",
                        "https://www.bilibili.com/video/BV1vK4y1m7wq",
                        "english",
                        "beginner",
                        75,
                        "english,cet4,vocabulary,video"
                ),
                new SeedResource(
                        "英语四级阅读理解技巧",
                        "https://www.bilibili.com/video/BV1bL411v7m3",
                        "english",
                        "beginner",
                        60,
                        "english,cet4,reading,video"
                ),
                new SeedResource(
                        "英语四级写作高频模板",
                        "https://www.bilibili.com/video/BV1fS4y1u7m8",
                        "english",
                        "beginner",
                        50,
                        "english,cet4,writing,template"
                ),
                new SeedResource(
                        "Python 官方教程",
                        "https://docs.python.org/3/tutorial/",
                        "python",
                        "beginner",
                        120,
                        "python,official,docs,beginner"
                ),
                new SeedResource(
                        "菜鸟教程 Python 教程",
                        "https://www.runoob.com/python3/python3-tutorial.html",
                        "python",
                        "beginner",
                        100,
                        "python,basic,runoob,beginner"
                )
        );
    }

    private String inferDomain(String title, String tags) {
        String normalized = ((title == null ? "" : title) + " "
                + (tags == null ? "" : tags)).toLowerCase();
        if (containsAny(normalized, "锅包肉", "美食", "菜谱", "做法", "食谱", "meishichina")) {
            return "general";
        }
        if (containsAny(normalized, "英语", "english", "cet4", "cet6", "四级", "六级", "词汇", "阅读", "写作")) {
            return "english";
        }
        if (containsAny(normalized, "java", "spring", "springboot", "jvm")) {
            return "java";
        }
        if (containsAny(normalized, "python", "numpy", "pandas", "爬虫")) {
            return "python";
        }
        if (containsAny(normalized, "mysql", "postgres", "sql", "数据库", "mybatis")) {
            return "database";
        }
        if (containsAny(normalized, "高数", "数学", "math", "线代", "概率")) {
            return "math";
        }
        if (containsAny(normalized, "vue", "react", "javascript", "css", "html", "前端")) {
            return "frontend";
        }
        if (containsAny(normalized, "linux", "shell", "docker", "运维")) {
            return "devops";
        }
        return "general";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record SeedResource(
            String title,
            String url,
            String domain,
            String level,
            Integer durationMinutes,
            String tags
    ) {
    }
}
