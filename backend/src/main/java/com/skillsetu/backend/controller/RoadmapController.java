package com.skillsetu.backend.controller;

import com.skillsetu.backend.entity.Roadmap;
import com.skillsetu.backend.service.RoadmapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roadmaps")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoadmapController {

    private final RoadmapService roadmapService;

    /**
     * Get all roadmaps for a student
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<Roadmap>> getStudentRoadmaps(@PathVariable Long studentId) {
        List<Roadmap> roadmaps = roadmapService.getStudentRoadmaps(studentId);
        return ResponseEntity.ok(roadmaps);
    }

    /**
     * Get specific roadmap by ID
     */
    @GetMapping("/{roadmapId}")
    public ResponseEntity<Roadmap> getRoadmapById(@PathVariable Long roadmapId) {
        return roadmapService.getRoadmapById(roadmapId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}