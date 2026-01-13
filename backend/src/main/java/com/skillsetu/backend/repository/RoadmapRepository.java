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
public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {
    Optional<Roadmap> findByStudentIdAndStatus(Long studentId, Roadmap.RoadmapStatus status);

    List<Roadmap> findByStudentId(Long studentId);

    @Query("SELECT COUNT(r) FROM Roadmap r WHERE r.student.college.id = :collegeId AND r.status = 'ACTIVE'")
    Long countActiveRoadmapsByCollege(@Param("collegeId") Long collegeId);
}