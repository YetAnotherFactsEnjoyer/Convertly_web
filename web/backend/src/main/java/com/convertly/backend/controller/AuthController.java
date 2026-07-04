package com.convertly.backend.controller;

import com.convertly.backend.dto.AuthDtos.AuthResponse;
import com.convertly.backend.dto.AuthDtos.LoginRequest;
import com.convertly.backend.dto.AuthDtos.RegisterRequest;
import com.convertly.backend.dto.AuthDtos.UserResponse;
import com.convertly.backend.entity.User;
import com.convertly.backend.service.JwtService;
import com.convertly.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final UserService users;
    private final JwtService jwtService;

    public AuthController(UserService users, JwtService jwtService) {
        this.users = users;
        this.jwtService = jwtService;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = users.register(request);
        JwtService.GeneratedToken token = jwtService.generate(user);
        return AuthResponse.from(user, token.token(), token.expiresAt());
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = users.authenticate(request);
        User user = users.requireCurrentUser(authentication);
        JwtService.GeneratedToken token = jwtService.generate(user);
        return AuthResponse.from(user, token.token(), token.expiresAt());
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Stateless JWT logout is handled by the client dropping its local token.
    }

    @GetMapping("/api/users/me")
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(users.requireCurrentUser(authentication));
    }
}
