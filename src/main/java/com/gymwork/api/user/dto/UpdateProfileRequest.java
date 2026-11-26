package com.gymwork.api.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String name,
        @DecimalMin(value = "50.1", message = "deve ser maior que 50 cm")
        @DecimalMax(value = "259.9", message = "deve ser menor que 260 cm")
        @Digits(integer = 3, fraction = 1) BigDecimal heightCm,
        @Past LocalDate birthDate) {
}
