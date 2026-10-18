package com.newtonite.ops.controller;

import com.newtonite.ops.dto.UserResponse;
import com.newtonite.ops.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user querying.
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Lists all registered users and operators.
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        log.info("REST: GET /api/users");
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Retrieves user details by UUID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        log.info("REST: GET /api/users/{}", id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Retrieves the active session / default operator user for test environments.
     */
    @GetMapping("/current")
    public ResponseEntity<UserResponse> getCurrentUser() {
        log.info("REST: GET /api/users/current");
        return ResponseEntity.ok(userService.getCurrentUser());
    }
}
