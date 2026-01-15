package com.skillsetu.backend.controller;

import com.skillsetu.backend.dto.*;
import com.skillsetu.backend.service.InterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
@Slf4j
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * Submit interview for evaluation
     * POST /api/interviews/evaluate
     */
    @PostMapping("/evaluate")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<InterviewResponse> evaluateInterview(
            @Valid @RequestBody InterviewRequest request) {

        log.info("Received interview evaluation request for student: {}, jobRole: {}",
                request.getStudentId(), request.getJobRole());

        try {
            InterviewResponse response = interviewService.evaluateAndGenerateRoadmap(request);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error evaluating interview", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Evaluation failed: " + e.getMessage()));
        }
    }

    /**
     * Get student's interview history
     * GET /api/interviews/student/{studentId}
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TPO')")
    public ResponseEntity<?> getStudentInterviews(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            var interviews = interviewService.getStudentInterviews(studentId, page, size);
            return ResponseEntity.ok(interviews);
        } catch (Exception e) {
            log.error("Error fetching interviews", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get specific interview details
     * GET /api/interviews/{interviewId}
     */
    @GetMapping("/{interviewId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TPO')")
    public ResponseEntity<?> getInterviewDetails(@PathVariable Long interviewId) {
        try {
            var interview = interviewService.getInterviewById(interviewId);
            return ResponseEntity.ok(interview);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private InterviewResponse createErrorResponse(String message) {
        InterviewResponse response = new InterviewResponse();
        response.setMessage(message);
        return response;
    }
}
