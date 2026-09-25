package mx.ipn.escom.tt.ensayosbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.dto.*;
import mx.ipn.escom.tt.ensayosbackend.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Autenticación (Tabla 55). Los errores los resuelve GlobalExceptionHandler. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/resend-token")
    public MessageResponse resendToken(@Valid @RequestBody CorreoRequest request) {
        return authService.resendToken(request);
    }

    @PostMapping("/verify-token")
    public AuthResponse verifyToken(@Valid @RequestBody VerifyTokenRequest request) {
        return authService.verifyToken(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/admin/request-otp")
    public MessageResponse requestAdminOtp(@Valid @RequestBody CorreoRequest request) {
        return authService.requestAdminOtp(request);
    }

    @PostMapping("/admin/verify-otp")
    public AuthResponse verifyAdminOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return authService.verifyAdminOtp(request);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody CorreoRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/refresh-token")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    // Recibe el refresh token (no el access) para poder cerrar sesión aunque el access ya haya expirado
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
