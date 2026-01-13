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
public interface InterviewRepository extends JpaRepository<Interview, Long> {
    Page<Interview> findByStudentIdOrderByCreatedAtDesc(Long studentId, Pageable pageable);

    List<Interview> findByStudentId(Long studentId);

    @Query("SELECT i FROM Interview i WHERE i.student.college.id = :collegeId ORDER BY i.createdAt DESC")
    Page<Interview> findByCollegeId(@Param("collegeId") Long collegeId, Pageable pageable);

    @Query("SELECT COUNT(i) FROM Interview i WHERE i.student.college.id = :collegeId")
    Long countByCollegeId(@Param("collegeId") Long collegeId);

    @Query("SELECT AVG(i.overallScore) FROM Interview i WHERE i.student.college.id = :collegeId")
    Double getAverageScoreByCollege(@Param("collegeId") Long collegeId);

    @Query("SELECT i FROM Interview i WHERE i.student.id = :studentId AND i.createdAt >= :since")
    List<Interview> findRecentInterviews(@Param("studentId") Long studentId,
                                         @Param("since") LocalDateTime since);
}