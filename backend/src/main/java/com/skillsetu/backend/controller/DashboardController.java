package com.skillsetu.backend.controller;

import com.skillsetu.backend.dto.DashboardResponseDTO;
import com.skillsetu.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/student/{studentId}")
    public DashboardResponseDTO getStudentDashboard(@PathVariable Long studentId) {
        return dashboardService.getStudentDashboard(studentId);
    }
}
