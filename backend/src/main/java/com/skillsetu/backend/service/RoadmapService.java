package com.skillsetu.backend.service;

import com.skillsetu.backend.entity.Roadmap;
import com.skillsetu.backend.repository.RoadmapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoadmapService {

    private final RoadmapRepository roadmapRepository;

    /**
     * Get all roadmaps for a student, ordered by creation date (newest first)
     */
    @Transactional(readOnly = true)
    public List<Roadmap> getStudentRoadmaps(Long studentId) {
        log.info("Fetching roadmaps for student ID: {}", studentId);
        return roadmapRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    /**
     * Get specific roadmap by ID
     */
    @Transactional(readOnly = true)
    public Optional<Roadmap> getRoadmapById(Long roadmapId) {
        return roadmapRepository.findById(roadmapId);
    }
}