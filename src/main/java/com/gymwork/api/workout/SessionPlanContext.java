package com.gymwork.api.workout;

import java.util.UUID;

/** De onde veio a sessão (planejamento, fase, semana e dia), copiado no início. */
record SessionPlanContext(
        UUID planId,
        String planName,
        UUID phaseId,
        String phaseName,
        UUID dayId,
        String dayName,
        UUID weekId,
        Integer weekNumber,
        boolean deload) {
}
