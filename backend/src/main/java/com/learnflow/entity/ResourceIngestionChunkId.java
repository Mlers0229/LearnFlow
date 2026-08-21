package com.learnflow.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ResourceIngestionChunkId implements Serializable {
    private UUID ingestionId;
    private int ordinal;
    public ResourceIngestionChunkId() {}
    public ResourceIngestionChunkId(UUID ingestionId, int ordinal) { this.ingestionId = ingestionId; this.ordinal = ordinal; }
    public UUID getIngestionId() { return ingestionId; }
    public void setIngestionId(UUID ingestionId) { this.ingestionId = ingestionId; }
    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }
    @Override public boolean equals(Object value) { return value instanceof ResourceIngestionChunkId other && ordinal == other.ordinal && Objects.equals(ingestionId, other.ingestionId); }
    @Override public int hashCode() { return Objects.hash(ingestionId, ordinal); }
}
