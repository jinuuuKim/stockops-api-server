package com.stockops.service;

import com.stockops.dto.CreateUserRequest;
import com.stockops.dto.ScopeMetadataDTO;
import com.stockops.dto.UpdateUserRequest;
import com.stockops.dto.UserDTO;
import com.stockops.entity.Role;
import com.stockops.entity.User;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.RoleRepository;
import com.stockops.repository.UserRepository;
import com.stockops.security.ScopeAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User management business logic.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Service
public class UserService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ScopeAccessService scopeAccessService;

    /**
     * Creates the service.
     *
     * @param userRepository user repository
     * @param roleRepository role repository
     * @param passwordEncoder password encoder
     */
    public UserService(final UserRepository userRepository,
                       final RoleRepository roleRepository,
                       final PasswordEncoder passwordEncoder,
                       final ScopeAccessService scopeAccessService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.scopeAccessService = scopeAccessService;
    }

    /**
     * Creates a new user with an encoded password.
     *
     * @param request user creation request
     * @return created user
     */
    @Transactional
    public UserDTO createUser(final CreateUserRequest request) {
        final Role role = resolveRole(request.role());

        final User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setEnabled(true);
        user.setRole(role);
        user.setScopeAssignments(scopeAccessService.normalizeAssignments(request.scopeAssignments()));

        return toDto(userRepository.save(user));
    }

    /**
     * Retrieves a user by identifier.
     *
     * @param id user identifier
     * @return user response
     */
    @Transactional(readOnly = true)
    public UserDTO getUserById(final Long id) {
        return toDto(findUserEntityById(id));
    }

    /**
     * Retrieves all users.
     *
     * @return user list
     */
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> getAllUsers(final Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toDto);
    }

    /**
     * Updates an existing user.
     *
     * @param id user identifier
     * @param request update payload
     * @return updated user response
     */
    @Transactional
    public UserDTO updateUser(final Long id, final UpdateUserRequest request) {
        final User user = findUserEntityById(id);

        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name());
        }

        if (request.role() != null && !request.role().isBlank()) {
            user.setRole(resolveRole(request.role()));
        }

        if (request.scopeAssignments() != null) {
            user.setScopeAssignments(scopeAccessService.normalizeAssignments(request.scopeAssignments()));
        }

        return toDto(userRepository.save(user));
    }

    /**
     * Deletes a user.
     *
     * @param id user identifier
     */
    @Transactional
    public void deleteUser(final Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }

        userRepository.deleteById(id);
    }

    /**
     * Retrieves a user entity by email.
     *
     * @param email user email
     * @return user entity
     */
    @Transactional(readOnly = true)
    public User getUserByEmail(final String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private User findUserEntityById(final Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private Role resolveRole(final String requestedRole) {
        final String roleName = (requestedRole == null || requestedRole.isBlank()) ? DEFAULT_ROLE : requestedRole;
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
    }

    private UserDTO toDto(final User user) {
        final ScopeMetadataDTO scopeMetadata = scopeAccessService.buildUserProfile(user).toDto();
        return new UserDTO(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().getName(),
                scopeMetadata,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
