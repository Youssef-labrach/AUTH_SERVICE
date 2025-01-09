package com.authservice.Controllers;

import com.authservice.JWT.JwtService;
import com.authservice.Models.AuthRequest;
import com.authservice.Models.User;
import com.authservice.Repos.UserRepository;
import com.authservice.Service.EmailService;
import com.authservice.Service.TwoFactorAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TwoFactorAuthService twoFactorAuthService;
    private final EmailService emailService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, TwoFactorAuthService twoFactorAuthService, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.twoFactorAuthService = twoFactorAuthService;
        this.emailService = emailService;
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/signin")
    public ResponseEntity<String> signIn(@RequestBody AuthRequest authRequest) {
        User user = userRepository.findByEmail(authRequest.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid password");
        }

        // Generate OTP code (in a real implementation, this should be randomized)
        int otpCode = (int) (Math.random() * 900000) + 100000; // Generate a 6-digit OTP
        user.setOtpCode(otpCode); // Save OTP in the database (update your User entity to include otpCode)
        userRepository.save(user);

        // Send OTP via email
        emailService.sendEmail(
                user.getEmail(),
                "Your OTP Code",
                "Your OTP code is: " + otpCode
        );

        return ResponseEntity.ok("OTP sent to your registered email address.");
    }


    @PostMapping("/enable-2fa")
    public ResponseEntity<String> enableTwoFactorAuth(@RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String secretKey = twoFactorAuthService.generateSecretKey();
        user.setSecretKey(secretKey);
        userRepository.save(user);

        String qrCodeUrl = String.format("otpauth://totp/AuthService:%s?secret=%s&issuer=AuthService", user.getEmail(), secretKey);

        // Send email with QR Code URL or secret key
        emailService.sendEmail(
                email,
                "Enable Two-Factor Authentication",
                "Scan this QR Code URL in your authenticator app: " + qrCodeUrl
        );

        return ResponseEntity.ok("2FA enabled. Check your email for the QR Code or Secret Key.");
    }


    @PostMapping("/verify-2fa")
    public ResponseEntity<String> verifyTwoFactorAuth(@RequestBody AuthRequest authRequest) {
        // Fetch the user
        User user = userRepository.findByEmail(authRequest.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));


        // Clear the OTP after validation
        user.setOtpCode(null);
        userRepository.save(user);

        // Generate JWT token
        String token = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        user.getEmail(),
                        "",
                        List.of()
                )
        );
        return ResponseEntity.ok(token);
    }


}
