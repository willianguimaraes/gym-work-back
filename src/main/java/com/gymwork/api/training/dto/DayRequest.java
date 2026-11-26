package com.gymwork.api.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;

/** @param dayOfWeek opcional (MONDAY…SUNDAY): dia da semana em que o treino costuma ser feito */
public record DayRequest(
        @NotBlank @Size(max = 60) String name,
        DayOfWeek dayOfWeek,
        @Size(max = 2000) String notes) {
}
