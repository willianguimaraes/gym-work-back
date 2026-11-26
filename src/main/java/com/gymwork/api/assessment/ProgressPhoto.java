package com.gymwork.api.assessment;

import com.gymwork.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** Foto de evolução. O arquivo fica no storage privado; aqui só a referência e os metadados. */
@Entity
@Table(name = "progress_photos")
public class ProgressPhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false, updatable = false)
    private BodyAssessment assessment;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PhotoPosition position;

    @Column(name = "storage_key", nullable = false, length = 200, updatable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 30, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(updatable = false)
    private Integer width;

    @Column(updatable = false)
    private Integer height;

    protected ProgressPhoto() {
    }

    public ProgressPhoto(
            BodyAssessment assessment,
            PhotoPosition position,
            String storageKey,
            String contentType,
            long sizeBytes,
            Integer width,
            Integer height) {
        this.assessment = assessment;
        this.userId = assessment.getUserId();
        this.position = position;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
    }

    void moveTo(PhotoPosition position) {
        this.position = position;
    }

    public BodyAssessment getAssessment() {
        return assessment;
    }

    public UUID getUserId() {
        return userId;
    }

    public PhotoPosition getPosition() {
        return position;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }
}
