package com.akshaya.elmsbackend.auth;

import com.akshaya.elmsbackend.common.exception.AppException;
import com.akshaya.elmsbackend.employee.entity.Employee;
import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import com.akshaya.elmsbackend.auth.entity.RefreshToken;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")

public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EmployeeRepository employeeRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService
    , RefreshTokenService refreshTokenService, EmployeeRepository employeeRepository
    ,RefreshTokenRepository refreshTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.employeeRepository=employeeRepository;
        this.refreshTokenRepository=refreshTokenRepository;
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        log.debug("Login request reached controller; verifying credentials");

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.employeeId(),
                            request.password()
                    )
            );
            log.debug("Login credentials verified; returning HTTP 200. JWT and refresh token issuance are not implemented yet");
            String refreshToken = refreshTokenService.generateRefreshToken();
            String hashedRefreshToken = refreshTokenService.hashRefreshToken(refreshToken);
            Employee employee = employeeRepository.findByEmployeeId(request.employeeId()).orElseThrow(() -> new RuntimeException("Employee not found"));
            RefreshToken tokenEntity = new RefreshToken(
                    employee,
                    hashedRefreshToken,
                    Instant.now(),                   // createdAt
                    Instant.now().plus(7, ChronoUnit.DAYS) // expiresAt
            );
            refreshTokenRepository.save(tokenEntity);
            ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                    .httpOnly(true)
                    .secure(false)
                    .path("/api/auth")
                    .maxAge(Duration.ofDays(7))
                    .sameSite("Lax")
                    .build();
            String accessToken = jwtService.generateAccessToken(employee.getEmployeeId());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(new LoginResponse(accessToken));

        } catch (AuthenticationException e) {
            log.debug("Login rejected; returning HTTP 401. Authentication failure type: {}",
                    e.getClass().getSimpleName());

            throw new AppException(AuthErrorCode.LOGIN_FAILED, "Invalid employee ID or password");
        }
    }
    @PostMapping("/refresh")
     public ResponseEntity<?> refreshAccessToken(@CookieValue(name="refresh_token", required = false) String rawRefreshToken) {
        if (rawRefreshToken == null) {
            throw new AppException(AuthErrorCode.TOKEN_MISSING, "Missing refresh token");
        }
        String hashedRefreshToken = refreshTokenService.hashRefreshToken(rawRefreshToken);
        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(hashedRefreshToken).orElse(null);
        if (tokenEntity == null) {
            throw new AppException(AuthErrorCode.TOKEN_INVALID, "Invalid refresh token");
        }
        if (tokenEntity.getRevokedAt() != null
                || !tokenEntity.getExpiresAt().isAfter(Instant.now())) {
            throw new AppException(AuthErrorCode.TOKEN_EXPIRED, "Expired or revoked refresh token");
        }
        Employee employee = tokenEntity.getEmployee();
        String newAccessToken = jwtService.generateAccessToken(employee.getEmployeeId());
        return ResponseEntity.ok(new LoginResponse(newAccessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(name = "refresh_token", required = false) String rawRefreshToken) {
        log.debug("Logout request reached controller");

        // Step 1: If a cookie exists, revoke the token in the database
        if (rawRefreshToken != null) {
            String hashedToken = refreshTokenService.hashRefreshToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(hashedToken)
                    .ifPresent(token -> {
                        token.revoke(Instant.now());
                        refreshTokenRepository.save(token);
                        log.debug("Refresh token revoked in database");
                    });
        }

        // Step 2: Delete the cookie from the browser
        ResponseCookie deleteCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/api/auth")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(Map.of("message", "Logged out successfully"));
    }
}
