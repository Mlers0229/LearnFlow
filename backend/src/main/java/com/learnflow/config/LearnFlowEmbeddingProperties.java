package com.learnflow.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "learnflow.embedding")
@Validated
public class LearnFlowEmbeddingProperties {

    private boolean enabled = false;
    @NotBlank
    private String provider = "openai-compatible";
    @NotBlank
    private String model = "text-embedding-3-small";
    @NotBlank
    private String version = "text-embedding-3-small-v1";
    @Min(1536)
    @Max(1536)
    private int dimensions = 1536;
    @Min(1)
    @Max(64)
    private int batchSize = 32;
    @Min(1)
    @Max(100)
    private int backfillPageSize = 20;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getBackfillPageSize() { return backfillPageSize; }
    public void setBackfillPageSize(int backfillPageSize) { this.backfillPageSize = backfillPageSize; }
}
