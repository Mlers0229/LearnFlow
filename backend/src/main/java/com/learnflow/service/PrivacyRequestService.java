package com.learnflow.service;

import com.learnflow.config.LearnFlowPrivacyProperties;
import com.learnflow.dto.AccountErasureRequest;
import com.learnflow.dto.PrivacyRequestResponse;
import com.learnflow.entity.User;
import com.learnflow.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PrivacyRequestService {
    public static final String EXPORT = "EXPORT";
    public static final String ERASURE = "ERASURE";

    private final PrivacyRequestStore store;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final ResourceSourceStore sourceStore;
    private final LearnFlowPrivacyProperties properties;

    public PrivacyRequestService(PrivacyRequestStore store, UserRepository users, PasswordEncoder passwordEncoder,
                                 RefreshTokenService refreshTokens, ResourceSourceStore sourceStore,
                                 LearnFlowPrivacyProperties properties) {
        this.store = store;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.sourceStore = sourceStore;
        this.properties = properties;
    }

    public PrivacyRequestResponse requestExport(long userId, String idempotencyKey) {
        requireEnabled();
        User user = requireActiveUser(userId);
        String key = normalizedIdempotencyKey(idempotencyKey);
        PrivacyRequestStore.PrivacyRequestRecord request = store.createOrGet(
                UUID.randomUUID(), userId, subjectHash(userId), EXPORT, fingerprint(EXPORT, userId, key));
        return response(request);
    }

    @Transactional
    public PrivacyRequestResponse requestErasure(long userId, String idempotencyKey, AccountErasureRequest input) {
        requireEnabled();
        User user = requireActiveUser(userId);
        if ("admin".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "管理员账户需由数据保护负责人执行受控擦除");
        }
        if (!passwordEncoder.matches(input.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        if (!("DELETE " + user.getUsername()).equals(input.getConfirmation())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账户擦除确认文本不匹配");
        }
        String key = normalizedIdempotencyKey(idempotencyKey);
        PrivacyRequestStore.PrivacyRequestRecord request = store.createOrGetActiveErasure(
                UUID.randomUUID(), userId, subjectHash(userId), fingerprint(ERASURE, userId, key)
        );
        store.cancelPendingExportsForErasure(userId);
        user.setStatus("DISABLED");
        users.save(user);
        refreshTokens.revokeAllForUser(userId);
        return response(request);
    }

    public PrivacyRequestResponse status(long userId, UUID requestId) {
        return response(store.findOwned(requestId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "隐私请求不存在")));
    }

    public ExportArtifact download(long userId, UUID requestId) {
        PrivacyRequestStore.PrivacyRequestRecord request = store.findOwned(requestId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据导出不存在"));
        if (!EXPORT.equals(request.type()) || !"SUCCEEDED".equals(request.status())
                || request.artifactObjectKey() == null || request.artifactDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据导出尚未可下载");
        }
        if (request.artifactExpiresAt() == null || !request.artifactExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.GONE, "数据导出已过期");
        }
        try (InputStream input = sourceStore.open(request.artifactObjectKey())) {
            byte[] bytes = StreamUtils.copyToByteArray(input);
            if (bytes.length > properties.getMaxExportBytes() || !sha256(bytes).equals(request.artifactSha256())) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "数据导出完整性校验失败");
            }
            return new ExportArtifact(bytes, "learnflow-data-export-" + requestId + ".json", request.artifactSha256());
        } catch (IOException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "数据导出暂时不可读取", failure);
        }
    }

    private User requireActiveUser(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账户已停用");
        }
        return user;
    }

    private PrivacyRequestResponse response(PrivacyRequestStore.PrivacyRequestRecord request) {
        boolean ready = EXPORT.equals(request.type()) && "SUCCEEDED".equals(request.status())
                && request.artifactObjectKey() != null && request.artifactDeletedAt() == null
                && request.artifactExpiresAt() != null
                && request.artifactExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC));
        return new PrivacyRequestResponse(request.id(), request.type(), request.status(), ready,
                request.artifactExpiresAt(), request.errorCode(), request.createdAt(), request.completedAt());
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "隐私请求处理暂不可用");
    }

    private String normalizedIdempotencyKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty()) return UUID.randomUUID().toString();
        if (key.length() < 8 || key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key 格式无效");
        }
        return key;
    }

    private String fingerprint(String type, long userId, String key) {
        return sha256((type + ":" + userId + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    private String subjectHash(long userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSubjectHashPepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(Long.toString(userId).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to create privacy subject reference", failure);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    public record ExportArtifact(byte[] bytes, String filename, String sha256) {}
}
