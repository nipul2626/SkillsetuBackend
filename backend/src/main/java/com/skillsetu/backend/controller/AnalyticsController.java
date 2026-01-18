package com.skillsetu.backend.controller;

import com.skillsetu.backend.dto.*;
import com.skillsetu.backend.service.AnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import java.util.HashMap;
import java.util.Map;


import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;




    // ==================== COLLEGE DASHBOARD ====================

    @GetMapping("/college/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<CollegeAnalyticsDTO> getCollegeAnalytics(
            @PathVariable Long collegeId) {

        log.info("📊 TPO requesting analytics for college: {}", collegeId);
        return ResponseEntity.ok(
                analyticsService.getCollegeAnalytics(collegeId)
        );
    }

    @GetMapping("/dashboard/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<Map<String, Object>> getDashboardStats(
            @PathVariable Long collegeId) {

        log.info("📈 TPO requesting dashboard stats for college: {}", collegeId);
        return ResponseEntity.ok(
                analyticsService.getDashboardStats(collegeId)
        );
    }

    @GetMapping("/attention/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<List<StudentPerformanceDTO>> getStudentsNeedingAttention(
            @PathVariable Long collegeId,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("⚠️ TPO requesting students needing attention for college: {}", collegeId);
        return ResponseEntity.ok(
                analyticsService.getStudentsNeedingAttention(collegeId, limit)
        );
    }

    // ==================== STUDENT LIST & FILTERING ====================

    @PostMapping("/students/list/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<Map<String, Object>> getFilteredStudents(
            @PathVariable Long collegeId,
            @Valid @RequestBody StudentFilterRequest filterRequest) {

        return ResponseEntity.ok(
                analyticsService.getFilteredStudents(collegeId, filterRequest)
        );
    }


    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<StudentPerformanceDTO> getStudentPerformance(
            @PathVariable Long studentId) {

        log.info("👤 TPO requesting performance for student: {}", studentId);
        return ResponseEntity.ok(
                analyticsService.getStudentPerformance(studentId)
        );
    }

    // ==================== ANALYTICS ====================

    @GetMapping("/skill-gaps/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<SkillGapDTO> getSkillGaps(
            @PathVariable Long collegeId,
            @RequestParam(required = false) String jobRole,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("📊 TPO requesting skill gaps for college: {}", collegeId);
        return ResponseEntity.ok(
                analyticsService.getSkillGaps(collegeId, jobRole, limit)
        );
    }

    @GetMapping("/trends/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<TrendDataDTO> getTrends(
            @PathVariable Long collegeId,
            @RequestParam(defaultValue = "30") int days) {

        log.info("📈 TPO requesting trends for college: {}", collegeId);
        return ResponseEntity.ok(
                analyticsService.getTrends(collegeId, days)
        );
    }

    // ==================== INSIGHTS ====================

    @GetMapping("/job-roles/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<Map<String, Long>> getJobRoleDistribution(
            @PathVariable Long collegeId) {

        log.info("💼 TPO requesting job role distribution for college: {}", collegeId);
        return ResponseEntity.ok(
                analyticsService.getCollegeAnalytics(collegeId)
                        .getJobRoleDistribution()
        );
    }

    @GetMapping("/top-performers/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<List<StudentPerformanceDTO>> getTopPerformers(
            @PathVariable Long collegeId,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("🏆 TPO requesting top performers for college: {}", collegeId);

        List<StudentPerformanceDTO> performers =
                analyticsService.getCollegeAnalytics(collegeId)
                        .getTopPerformers();

        return ResponseEntity.ok(
                performers.size() > limit
                        ? performers.subList(0, limit)
                        : performers
        );
    }

    @GetMapping("/branches/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<Map<String, Object>> getBranchStats(
            @PathVariable Long collegeId) {

        log.info("🎓 TPO requesting branch stats for college: {}", collegeId);
        return ResponseEntity.ok(
                analyticsService.getBranchWiseStats(collegeId)
        );
    }

    @GetMapping("/readiness/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<Map<String, Long>> getReadinessBreakdown(
            @PathVariable Long collegeId) {

        log.info("📊 TPO requesting readiness breakdown for college: {}", collegeId);
        return ResponseEntity.ok(
                (Map<String, Long>) analyticsService
                        .getDashboardStats(collegeId)
                        .get("readinessBreakdown")
        );
    }


}
