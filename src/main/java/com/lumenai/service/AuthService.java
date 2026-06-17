package com.lumenai.service;

import com.lumenai.dto.JwtResponse;
import com.lumenai.entity.User;
import com.lumenai.repository.UserRepository;
import com.lumenai.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final EmailService emailService;

    public String register(String name, String email, String password) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already in use");
        }

        String verificationToken = UUID.randomUUID().toString();

        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .verified(false)
                .verificationToken(verificationToken)
                .build();
        userRepository.save(user);

        // Send verification email (logs to console in local dev when mail is disabled)
        emailService.sendVerificationEmail(email, name, verificationToken);

        return "User registered successfully. Please check your email to verify your account.";
    }

    public String verify(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired verification token"));

        user.setVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);

        return "Email verified successfully. You can now log in.";
    }

    public JwtResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        // Note: Email verification check is optional — uncomment in production
        // if (!user.isVerified()) {
        //     throw new RuntimeException("Please verify your email before logging in.");
        // }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String token = jwtUtil.generateToken(userDetails);

        return new JwtResponse(token, user.getEmail(), user.getName());
    }
}
