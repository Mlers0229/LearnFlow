package com.learnflow.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceStorageProductionValidatorTest {
    @Test
    void productionRequiresS3WhileDevelopmentAllowsBoundedFilesystemStorage() {
        ResourceIngestionProperties properties = new ResourceIngestionProperties();
        assertThatThrownBy(() -> new ResourceStorageProductionValidator("production", properties).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("S3");
        assertThatCode(() -> new ResourceStorageProductionValidator("development", properties).validate())
                .doesNotThrowAnyException();
        properties.getStorage().setType("s3");
        properties.getStorage().setBucket("learnflow-production");
        assertThatCode(() -> new ResourceStorageProductionValidator("production", properties).validate())
                .doesNotThrowAnyException();
    }
}
