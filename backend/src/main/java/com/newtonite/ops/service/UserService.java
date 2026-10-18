package com.newtonite.ops.service;

import com.newtonite.ops.dto.UserResponse;
import com.newtonite.ops.exception.ResourceNotFoundException;
import com.newtonite.ops.model.entity.User;
import com.newtonite.ops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing user operations and user resolution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Lists all operators and users in the system.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        log.info("Fetching all registered users");
        return userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    /**
     * Retrieves user details by unique UUID.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        log.info("Fetching user by id: {}", id);
        User user = getUserEntity(id);
        return UserResponse.fromEntity(user);
    }

    /**
     * Internal lookup returning the managed JPA User entity.
     */
    @Transactional(readOnly = true)
    public User getUserEntity(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    /**
     * Returns a default operator user for frontend testing / local session emulation.
     * Selects the first user in the database or throws if none exist.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        log.info("Fetching default active session user");
        return userRepository.findAll().stream()
                .findFirst()
                .map(UserResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("No users currently exist in database"));
    }
}
