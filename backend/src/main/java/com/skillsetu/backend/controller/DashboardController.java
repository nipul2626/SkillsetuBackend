package com.skillsetu.backend.controller;

import com.skillsetu.backend.dto.DashboardResponseDTO;
import com.skillsetu.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Get complete dashboard data for a student
     * Returns readiness score, stats, recent activities, skills breakdown
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<DashboardResponseDTO> getStudentDashboard(@PathVariable Long studentId) {
        try {
            DashboardResponseDTO dashboard = dashboardService.getStudentDashboard(studentId);
            return ResponseEntity.ok(dashboard);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}