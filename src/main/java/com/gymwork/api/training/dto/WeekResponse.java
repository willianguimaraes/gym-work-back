package com.gymwork.api.training.dto;

import com.gymwork.api.training.TrainingWeek;
import java.time.LocalDate;
import java.util.UUID;

public record WeekResponse(UUID id, int weekNumber, LocalDate startDate, boolean deload, String notes) {

    public static WeekResponse from(TrainingWeek week) {
        return new WeekResponse(
                week.getId(), week.getWeekNumber(), week.getStartDate(), week.isDeload(), week.getNotes());
    }
}
