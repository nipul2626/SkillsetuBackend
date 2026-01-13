package com.skillsetu.backend.controller;

import com.skillsetu.backend.dto.LoginRequest;
import com.skillsetu.backend.dto.LoginResponse;
import com.skillsetu.backend.dto.RegisterRequest;
import com.skillsetu.backend.security.JwtTokenProvider;
import com.skillsetu.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt received for email: {}", request.getEmail());

        try {
            // This is where the magic (and the failure) happens
            System.out.println("DEBUG: Email: [" + request.getEmail() + "]");
            System.out.println("DEBUG: Password: [" + request.getPassword() + "]");
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            String token = tokenProvider.generateToken(authentication);

            LoginResponse response = new LoginResponse();
            response.setToken(token);
            response.setEmail(request.getEmail());
            response.setMessage("Login successful");

            log.info("User {} authenticated successfully", request.getEmail());
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            log.error("Authentication failed: Invalid password for user {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid email or password");
        } catch (Exception e) {
            log.error("Authentication failed for user {}: {}", request.getEmail(), e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication failed: " + e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());
        authService.registerUser(request);
        return ResponseEntity.ok("User registered successfully");
    }
}