package com.skillsetu.backend.controller;

import com.skillsetu.backend.dto.ProfileResponse;
import com.skillsetu.backend.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);
    private final ProfileService profileService;

    @GetMapping("/{studentId}")
    public ResponseEntity<?> getStudentProfile(
            @PathVariable Long studentId,
            @RequestHeader("Authorization") String token
    ) {
        log.info("Profile request for student ID: {}", studentId);

        try {
            ProfileResponse profile = profileService.getStudentProfile(studentId);
            return ResponseEntity.ok(profile);

        } catch (RuntimeException e) {
            log.error("Failed to fetch profile: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            log.error("Unexpected error fetching profile: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to load profile");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}