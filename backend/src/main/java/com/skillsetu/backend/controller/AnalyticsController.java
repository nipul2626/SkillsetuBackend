package com.skillsetu.backend.controller;

import com.skillsetu.backend.dto.CollegeAnalyticsDTO;
import com.skillsetu.backend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Get college-wide analytics (TPO only)
     */
    @GetMapping("/college/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<CollegeAnalyticsDTO> getCollegeAnalytics(
            @PathVariable Long collegeId) {
        var analytics = analyticsService.getCollegeAnalytics(collegeId);
        return ResponseEntity.ok(analytics);
    }

    /**
     * Get dashboard statistics
     */
    @GetMapping("/dashboard/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<?> getDashboardStats(@PathVariable Long collegeId) {
        var stats = analyticsService.getDashboardStats(collegeId);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get students needing attention
     */
    @GetMapping("/attention/{collegeId}")
    @PreAuthorize("hasRole('TPO')")
    public ResponseEntity<?> getStudentsNeedingAttention(
            @PathVariable Long collegeId,
            @RequestParam(defaultValue = "10") int limit) {
        var students = analyticsService.getStudentsNeedingAttention(collegeId, limit);
        return ResponseEntity.ok(students);
    }
}