package com.gymwork.api.exercise;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MuscleGroupRepository extends JpaRepository<MuscleGroup, String> {

    List<MuscleGroup> findAllByOrderByPositionAsc();
}
