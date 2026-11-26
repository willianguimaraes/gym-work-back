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
import java.math.BigDecimal;
import java.util.UUID;

/** Uma medida informada (uma linha por medida; medida não informada não tem linha). */
@Entity
@Table(name = "body_measurements")
public class BodyMeasurement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false, updatable = false)
    private BodyAssessment assessment;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private MeasurementType type;

    @Column(name = "value_cm", nullable = false, precision = 5, scale = 1)
    private BigDecimal valueCm;

    protected BodyMeasurement() {
    }

    public BodyMeasurement(BodyAssessment assessment, MeasurementType type, BigDecimal valueCm) {
        this.assessment = assessment;
        this.userId = assessment.getUserId();
        this.type = type;
        this.valueCm = BodyAssessment.scale(valueCm, 1);
    }

    void changeValue(BigDecimal valueCm) {
        this.valueCm = BodyAssessment.scale(valueCm, 1);
    }

    public BodyAssessment getAssessment() {
        return assessment;
    }

    public MeasurementType getType() {
        return type;
    }

    public BigDecimal getValueCm() {
        return valueCm;
    }
}
