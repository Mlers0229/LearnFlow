package com.learnflow.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemResourceSourceStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void storesWithinRootAndRejectsTraversal() throws Exception {
        FileSystemResourceSourceStore store = new FileSystemResourceSourceStore(temporaryDirectory);
        byte[] bytes = "safe source".getBytes(StandardCharsets.UTF_8);
        store.put("learnflow/resources/1/source", new ByteArrayInputStream(bytes), bytes.length, "text/plain");
        assertThat(store.open("learnflow/resources/1/source").readAllBytes()).isEqualTo(bytes);
        assertThatThrownBy(() -> store.open("../secret"))
                .isInstanceOf(java.io.IOException.class);
    }
}
