package com.skillsetu.backend.service;

import com.skillsetu.backend.dto.RegisterRequest;
import com.skillsetu.backend.entity.User;
import com.skillsetu.backend.entity.College;
import com.skillsetu.backend.repository.UserRepository;
import com.skillsetu.backend.repository.CollegeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void registerUser(RegisterRequest request) {
        // 1. Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already in use!");
        }

        // 2. Create new user entity
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());

        // Map String role to Enum
        user.setRole(User.UserRole.valueOf(request.getRole().toUpperCase()));

        // 3. Associate with college if ID is provided
        if (request.getCollegeId() != null) {
            College college = collegeRepository.findById(request.getCollegeId())
                    .orElseThrow(() -> new RuntimeException("College not found"));
            user.setCollege(college);
        }

        // 4. Set additional student details if applicable
        user.setBranch(request.getBranch());
        user.setYearOfStudy(request.getYearOfStudy());

        // Set default stats
        user.setPlacementReadinessScore(0.0);
        user.setTotalInterviewsTaken(0);
        user.setAverageScore(0.0);

        userRepository.save(user);
    }
}