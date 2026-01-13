package com.skillsetu.backend.repository;

import com.skillsetu.backend.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    List<User> findByCollegeIdAndRole(Long collegeId, User.UserRole role);

    @Query("SELECT u FROM User u WHERE u.college.id = :collegeId AND u.role = 'STUDENT'")
    Page<User> findStudentsByCollege(@Param("collegeId") Long collegeId, Pageable pageable);

    @Query("SELECT AVG(u.placementReadinessScore) FROM User u WHERE u.college.id = :collegeId AND u.role = 'STUDENT'")
    Double getAverageReadinessScoreByCollege(@Param("collegeId") Long collegeId);
}