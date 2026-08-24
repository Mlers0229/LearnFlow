package com.learnflow.service;

import com.learnflow.config.LearnFlowPrivacyProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PrivacyRequestWorkerTest {
    @Test
    void erasureDeletesEveryObjectBeforeDatabaseErasure() throws Exception {
        PrivacyRequestStore store = mock(PrivacyRequestStore.class);
        PrivacyDataExportService exports = mock(PrivacyDataExportService.class);
        PrivacyErasurePersistence erasure = mock(PrivacyErasurePersistence.class);
        ResourceSourceStore objects = mock(ResourceSourceStore.class);
        LearnFlowPrivacyProperties properties = new LearnFlowPrivacyProperties();
        PrivacyRequestWorker worker = new PrivacyRequestWorker(
                store, exports, erasure, objects, properties, new SimpleMeterRegistry());
        UUID id = UUID.randomUUID();
        var request = record(id, "ERASURE", 7L);
        when(store.objectKeysForErasure(7L)).thenReturn(List.of("resource/one", "privacy/exports/two.json"));

        worker.execute(request);

        verify(objects).delete("resource/one");
        verify(objects).delete("privacy/exports/two.json");
        verify(erasure).erase(id, 7L);
    }

    @Test
    void erasureWaitsForRunningExportBeforeDeletingObjectsOrData() throws Exception {
        PrivacyRequestStore store = mock(PrivacyRequestStore.class);
        PrivacyDataExportService exports = mock(PrivacyDataExportService.class);
        PrivacyErasurePersistence erasure = mock(PrivacyErasurePersistence.class);
        ResourceSourceStore objects = mock(ResourceSourceStore.class);
        LearnFlowPrivacyProperties properties = new LearnFlowPrivacyProperties();
        PrivacyRequestWorker worker = new PrivacyRequestWorker(
                store, exports, erasure, objects, properties, new SimpleMeterRegistry());
        var request = record(UUID.randomUUID(), "ERASURE", 7L);
        when(store.hasRunningExport(7L)).thenReturn(true);

        worker.execute(request);

        verify(store).cancelPendingExportsForErasure(7L);
        verify(store).failOrRetry(eq(request), any(IllegalStateException.class));
        verify(store, never()).objectKeysForErasure(7L);
        verify(erasure, never()).erase(any(), anyLong());
    }

    @Test
    void exportStoresBoundedArtifactAndCompletesManifest() throws Exception {
        PrivacyRequestStore store = mock(PrivacyRequestStore.class);
        PrivacyDataExportService exports = mock(PrivacyDataExportService.class);
        PrivacyErasurePersistence erasure = mock(PrivacyErasurePersistence.class);
        ResourceSourceStore objects = mock(ResourceSourceStore.class);
        LearnFlowPrivacyProperties properties = new LearnFlowPrivacyProperties();
        PrivacyRequestWorker worker = new PrivacyRequestWorker(
                store, exports, erasure, objects, properties, new SimpleMeterRegistry());
        UUID id = UUID.randomUUID();
        byte[] payload = "{\"schemaVersion\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(exports.build(7L, id)).thenReturn(payload);

        worker.execute(record(id, "EXPORT", 7L));

        verify(objects).put(eq("privacy/exports/" + id + ".json"), any(InputStream.class),
                eq((long) payload.length), eq("application/json"));
        verify(store).completeExport(eq(id), eq("privacy/exports/" + id + ".json"), anyString(),
                eq((long) payload.length), any(OffsetDateTime.class));
    }

    private PrivacyRequestStore.PrivacyRequestRecord record(UUID id, String type, Long userId) {
        return new PrivacyRequestStore.PrivacyRequestRecord(
                id, userId, type, "RUNNING", 1, 5, null, null, null,
                null, null, null, OffsetDateTime.now(ZoneOffset.UTC), null);
    }
}
