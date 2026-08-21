package com.learnflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@IdClass(ResourceIngestionChunkId.class)
@Table(name = "resource_ingestion_chunk")
public class ResourceIngestionChunk {
    @Id @Column(name = "ingestion_id") private UUID ingestionId;
    @Id private int ordinal;
    @Column(name = "chunk_id", nullable = false) private UUID chunkId;
    @Column(name = "char_start", nullable = false) private int charStart;
    @Column(name = "char_end", nullable = false) private int charEnd;
    public UUID getIngestionId() { return ingestionId; }
    public void setIngestionId(UUID ingestionId) { this.ingestionId = ingestionId; }
    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }
    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }
    public int getCharStart() { return charStart; }
    public void setCharStart(int charStart) { this.charStart = charStart; }
    public int getCharEnd() { return charEnd; }
    public void setCharEnd(int charEnd) { this.charEnd = charEnd; }
}
