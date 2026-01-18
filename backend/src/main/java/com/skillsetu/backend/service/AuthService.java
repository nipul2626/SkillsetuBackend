package com.skillsetu.backend.service;

import com.skillsetu.backend.dto.RegisterRequest;
import com.skillsetu.backend.entity.College;
import com.skillsetu.backend.entity.User;
import com.skillsetu.backend.repository.CollegeRepository;
import com.skillsetu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User registerUser(RegisterRequest request) {

        log.info("Registering new user: {}", request.getEmail());

        // 1️⃣ Prevent duplicate email
        userRepository.findByEmail(request.getEmail())
                .ifPresent(u -> {
                    throw new RuntimeException("Email already registered");
                });

        // 2️⃣ Find or create college
        College college = collegeRepository
                .findByName(request.getCollegeName())
                .orElseGet(() -> {
                    College c = new College();
                    c.setName(request.getCollegeName());

                    // ✅ REQUIRED FIELDS (FIXES DB ERROR)
                    c.setLocation("Unknown");
                    c.setCity("Unknown");
                    c.setState("Unknown");

                    c.setIsActive(true);

                    return collegeRepository.save(c);
                });


        // 3️⃣ Create student user
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setCollege(college);
        user.setBranch(request.getBranch());
        user.setYearOfStudy(
                request.getYearOfStudy() != null ? request.getYearOfStudy() : 1
        );

        // 🔐 Role is ALWAYS STUDENT on signup
        user.setRole(User.UserRole.ROLE_STUDENT);

        // Initial stats
        user.setIsActive(true);
        user.setPlacementReadinessScore(0.0);
        user.setTotalInterviewsTaken(0);
        user.setAverageScore(0.0);

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getEmail());

        return savedUser;
    }
}
