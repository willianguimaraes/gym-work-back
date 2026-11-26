package com.gymwork.api.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    @Query("select p from UserProfile p join fetch p.user where p.userId = :userId")
    Optional<UserProfile> findWithUser(@Param("userId") UUID userId);
}
