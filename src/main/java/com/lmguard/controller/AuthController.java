package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.dto.auth.AuthResponse;
import com.lmguard.dto.auth.LoginRequest;
import com.lmguard.dto.auth.RegisterRequest;
import com.lmguard.dto.auth.UserResponse;
import com.lmguard.security.SecurityUtils;
import com.lmguard.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "1. Authentication", description = "Account creation and token issuance")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Register an inspector or administrator",
            description = """
                    Creates an account and returns a JWT immediately, so a new user can start
                    working without a second call.

                    `role` defaults to `INSPECTOR`. In a real deployment, creating `ADMIN`
                    accounts should be restricted; this endpoint is open for the MVP so a team
                    can bootstrap itself.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "EMAIL_ALREADY_REGISTERED", content = @io.swagger.v3.oas.annotations.media.Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", response));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange credentials for a JWT",
            description = """
                    Returns a bearer token. Send it on every subsequent request as:

                    `Authorization: Bearer <token>`

                    In Swagger UI, paste it into the **Authorize** dialog once.
                    """)
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(request)));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "The currently authenticated user",
            description = "Useful for the frontend to restore session state after a refresh.")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success(authService.currentUser(SecurityUtils.currentUserId())));
    }
}
