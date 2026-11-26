package com.gymwork.api.assessment.dto;

import com.gymwork.api.assessment.PhotoPosition;
import java.math.BigDecimal;
import java.util.List;

/**
 * Comparativo entre duas avaliações ({@code from} é a mais antiga). A diferença só existe quando o
 * valor foi informado nas duas; nada é estimado.
 */
public record ComparisonResponse(
        AssessmentResponse from,
        AssessmentResponse to,
        long daysBetween,
        List<Item> items,
        List<PhotoPair> photos) {

    /** @param metric WEIGHT, BODY_FAT, HEIGHT ou o tipo da medida (CHEST, WAIST…) */
    public record Item(String metric, String unit, BigDecimal fromValue, BigDecimal toValue, BigDecimal difference) {
    }

    public record PhotoPair(PhotoPosition position, PhotoResponse from, PhotoResponse to) {
    }
}
