package com.skillsetu.backend.config;

import com.skillsetu.backend.entity.College;
import com.skillsetu.backend.entity.User;
import com.skillsetu.backend.repository.CollegeRepository;
import com.skillsetu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer {

    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initDatabase() {
        return args -> {
            // Check if test data already exists
            if (userRepository.existsByEmail("student@test.com")) {
                log.info("Test data already exists. Skipping initialization.");
                return;
            }

            log.info("🚀 Initializing test data...");

            // Create test college
            College college = new College();
            college.setName("Test College");
            college.setLocation("Test City");
            college.setEstablishedYear(2020);
            college.setDescription("This is a test college for development");
            college.setIsActive(true);
            college = collegeRepository.save(college);
            log.info("✅ Created test college: {}", college.getName());

            // Create test student with HASHED password
            User student = new User();
            student.setEmail("student@test.com");
            student.setPassword(passwordEncoder.encode("password123")); // HASH THE PASSWORD
            student.setFullName("Test Student");
            student.setRole(User.UserRole.STUDENT);
            student.setCollege(college);
            student.setBranch("Computer Science");
            student.setYearOfStudy(3);
            student.setPlacementReadinessScore(0.0);
            student.setTotalInterviewsTaken(0);
            student.setAverageScore(0.0);
            student.setIsActive(true);
            student = userRepository.save(student);
            log.info("✅ Created test student: {} (ID: {})", student.getEmail(), student.getId());

            // Create test TPO
            User tpo = new User();
            tpo.setEmail("tpo@test.com");
            tpo.setPassword(passwordEncoder.encode("password123"));
            tpo.setFullName("Test TPO");
            tpo.setRole(User.UserRole.TPO);
            tpo.setCollege(college);
            tpo.setIsActive(true);
            tpo = userRepository.save(tpo);
            log.info("✅ Created test TPO: {} (ID: {})", tpo.getEmail(), tpo.getId());

            log.info("✅ Test data initialization complete!");
            log.info("📝 Login credentials:");
            log.info("   Student - Email: student@test.com, Password: password123");
            log.info("   TPO - Email: tpo@test.com, Password: password123");
        };
    }
}