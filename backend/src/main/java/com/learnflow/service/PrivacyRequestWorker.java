package com.learnflow.service;

import com.learnflow.config.LearnFlowPrivacyProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class PrivacyRequestWorker {
    private static final Logger log = LoggerFactory.getLogger(PrivacyRequestWorker.class);

    private final PrivacyRequestStore requests;
    private final PrivacyDataExportService exports;
    private final PrivacyErasurePersistence erasure;
    private final ResourceSourceStore objectStore;
    private final LearnFlowPrivacyProperties properties;
    private final MeterRegistry meters;
    private final String workerId;

    public PrivacyRequestWorker(PrivacyRequestStore requests, PrivacyDataExportService exports,
                                PrivacyErasurePersistence erasure, ResourceSourceStore objectStore,
                                LearnFlowPrivacyProperties properties, MeterRegistry meters) {
        this.requests = requests;
        this.exports = exports;
        this.erasure = erasure;
        this.objectStore = objectStore;
        this.properties = properties;
        this.meters = meters;
        this.workerId = resolveWorkerId();
    }

    @Scheduled(fixedDelayString = "${LEARNFLOW_PRIVACY_POLL_INTERVAL_MS:1000}")
    public void poll() {
        if (!properties.isEnabled()) return;
        requests.claimNext(workerId).ifPresent(this::execute);
    }

    @Scheduled(cron = "${LEARNFLOW_PRIVACY_EXPORT_CLEANUP_CRON:0 11 * * * *}", zone = "UTC")
    public void purgeExpiredExports() {
        if (!properties.isEnabled()) return;
        for (PrivacyRequestStore.ExpiredArtifact artifact : requests.findExpiredArtifacts(50)) {
            try {
                objectStore.delete(artifact.objectKey());
                requests.markArtifactDeleted(artifact.id(), artifact.objectKey());
                meters.counter("learnflow.privacy.artifact.cleanup", "outcome", "success").increment();
            } catch (Exception failure) {
                meters.counter("learnflow.privacy.artifact.cleanup", "outcome", "failure").increment();
                log.warn("Privacy artifact cleanup failed requestId={} errorType={}",
                        artifact.id(), failure.getClass().getSimpleName());
            }
        }
        int purged = requests.purgeCompletedBefore(OffsetDateTime.now(ZoneOffset.UTC).minus(properties.getRequestRetention()));
        if (purged > 0) meters.counter("learnflow.privacy.request.cleanup").increment(purged);
    }

    void execute(PrivacyRequestStore.PrivacyRequestRecord request) {
        long started = System.nanoTime();
        String outcome = "success";
        try {
            if (PrivacyRequestService.EXPORT.equals(request.type())) {
                executeExport(request);
            } else if (PrivacyRequestService.ERASURE.equals(request.type())) {
                executeErasure(request);
            } else {
                throw new IllegalStateException("Unsupported privacy request type");
            }
        } catch (Exception failure) {
            outcome = "failure";
            requests.failOrRetry(request, failure);
            log.warn("Privacy request attempt failed requestId={} type={} errorType={}",
                    request.id(), request.type(), failure.getClass().getSimpleName());
        } finally {
            meters.timer("learnflow.privacy.request.duration",
                            Tags.of("type", request.type().toLowerCase(), "outcome", outcome))
                    .record(java.time.Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private void executeExport(PrivacyRequestStore.PrivacyRequestRecord request) throws Exception {
        if (request.userId() == null) throw new IllegalStateException("Export subject is unavailable");
        byte[] bytes = exports.build(request.userId(), request.id());
        String key = "privacy/exports/" + request.id() + ".json";
        objectStore.put(key, new ByteArrayInputStream(bytes), bytes.length, "application/json");
        try {
            requests.completeExport(request.id(), key, PrivacyRequestService.sha256(bytes), bytes.length,
                    OffsetDateTime.now(ZoneOffset.UTC).plus(properties.getExportTtl()));
        } catch (RuntimeException failure) {
            objectStore.delete(key);
            throw failure;
        }
    }

    private void executeErasure(PrivacyRequestStore.PrivacyRequestRecord request) throws Exception {
        if (request.userId() == null) throw new IllegalStateException("Erasure subject is unavailable");
        requests.cancelPendingExportsForErasure(request.userId());
        if (requests.hasRunningExport(request.userId())) {
            throw new IllegalStateException("An export is still running for the erasure subject");
        }
        for (String objectKey : requests.objectKeysForErasure(request.userId())) {
            objectStore.delete(objectKey);
        }
        erasure.erase(request.id(), request.userId());
    }

    private String resolveWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":privacy:" + UUID.randomUUID();
        } catch (Exception ignored) {
            return "privacy-worker:" + UUID.randomUUID();
        }
    }
}
