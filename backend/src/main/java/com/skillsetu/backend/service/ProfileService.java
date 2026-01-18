package com.skillsetu.backend.service;

import com.skillsetu.backend.dto.ProfileResponse;
import com.skillsetu.backend.entity.User;
import com.skillsetu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ProfileResponse getStudentProfile(Long studentId) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        ProfileResponse response = new ProfileResponse();
        response.setId(user.getId());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setCollege(user.getCollege() != null ? user.getCollege().getName() : "N/A");
        response.setBranch(user.getBranch());
        response.setYear(getYearString(user.getYearOfStudy()));
        response.setCgpa(0.0); // Placeholder - add CGPA field to User entity if needed
        response.setTotalInterviews(user.getTotalInterviewsTaken());
        response.setSkillsLearned(0); // Placeholder - calculate from skills table later
        response.setAverageScore(user.getAverageScore() != null ? user.getAverageScore().intValue() : 0);
        response.setPlacementReadinessScore(user.getPlacementReadinessScore());

        return response;
    }

    private String getYearString(Integer year) {
        if (year == null) return "N/A";
        switch (year) {
            case 1: return "1st Year";
            case 2: return "2nd Year";
            case 3: return "3rd Year";
            case 4: return "4th Year";
            default: return year + "th Year";
        }
    }
}