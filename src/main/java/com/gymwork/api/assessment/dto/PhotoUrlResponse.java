package com.gymwork.api.assessment.dto;

import java.time.Instant;

public record PhotoUrlResponse(String url, Instant expiresAt) {
}
