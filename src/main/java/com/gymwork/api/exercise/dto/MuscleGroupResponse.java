package com.gymwork.api.exercise.dto;

import com.gymwork.api.exercise.MuscleGroup;

public record MuscleGroupResponse(String code, String name) {

    public static MuscleGroupResponse from(MuscleGroup group) {
        return new MuscleGroupResponse(group.getCode(), group.getName());
    }
}
