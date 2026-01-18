package com.skillsetu.backend.repository;

import com.skillsetu.backend.entity.Roadmap;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {

    List<Roadmap> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    long countByStudentId(Long studentId);

    // ✅ ADD THIS
    long countByStudent_College_Id(Long collegeId);
}
