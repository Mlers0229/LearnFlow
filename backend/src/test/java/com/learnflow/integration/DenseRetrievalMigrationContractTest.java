package com.learnflow.integration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DenseRetrievalMigrationContractTest {

    @Test
    void v10DefinesVersionedVectorsHnswAndLeastPrivilegeGrants() throws Exception {
        String migration = resource("db/migration/V10__add_pgvector_dense_retrieval.sql")
                .toLowerCase();

        assertThat(migration).contains("create extension if not exists vector");
        assertThat(migration).contains("create table embedding_model_version");
        assertThat(migration).contains("create table resource_chunk_embedding");
        assertThat(migration).contains("embedding vector(1536) not null");
        assertThat(migration).contains("using hnsw (embedding vector_cosine_ops)");
        assertThat(migration).contains("'resource_embedding'");
        assertThat(migration).contains("grant select on embedding_model_version, resource_chunk_embedding to learnflow_agent");
        assertThat(migration).doesNotContain("grant insert on resource_chunk_embedding to learnflow_agent");
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = DenseRetrievalMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(name)) {
            assertThat(input).as("migration resource %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
