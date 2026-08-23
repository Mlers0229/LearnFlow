package com.learnflow.integration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SparseRetrievalMigrationContractTest {

    @Test
    void v13DefinesGeneratedFtsDocumentGinAndReadOnlyAgentAccess() throws Exception {
        String migration = resource("db/migration/V13__add_sparse_full_text_retrieval.sql")
                .toLowerCase();

        assertThat(migration).contains("add column search_vector tsvector");
        assertThat(migration).contains("generated always as");
        assertThat(migration).contains("to_tsvector('simple'");
        assertThat(migration).contains("using gin (search_vector)");
        assertThat(migration).contains("grant select on resource_bank, resource_ingestion, resource_chunk");
        assertThat(migration).doesNotContain("grant insert on resource_chunk");
        assertThat(migration).doesNotContain("grant update on resource_chunk");
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = SparseRetrievalMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(name)) {
            assertThat(input).as("migration resource %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
