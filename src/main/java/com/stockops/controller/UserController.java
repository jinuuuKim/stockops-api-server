package com.stockops.controller;

import com.stockops.dto.CreateUserRequest;
import com.stockops.dto.UpdateUserRequest;
import com.stockops.dto.UserDTO;
import com.stockops.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User management API controller.
 *
 * @author StockOps Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    /**
     * Creates the controller.
     *
     * @param userService user service
     */
    public UserController(final UserService userService) {
        this.userService = userService;
    }

    /**
     * Lists all users.
     *
     * @return user list
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('USER_READ')")
    public ResponseEntity<Page<UserDTO>> getAllUsers(@PageableDefault(size = 20) final Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    /**
     * Gets a user by id.
     *
     * @param id user id
     * @return user response
     */
    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('USER_READ')")
    public ResponseEntity<UserDTO> getUserById(@PathVariable final Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Creates a user.
     *
     * @param request creation request
     * @return created user
     */
    @PostMapping
    @PreAuthorize("@permissionChecker.hasPermission('USER_CREATE')")
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody final CreateUserRequest request) {
        return ResponseEntity.status(201).body(userService.createUser(request));
    }

    /**
     * Updates a user.
     *
     * @param id user id
     * @param request update request
     * @return updated user
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('USER_UPDATE')")
    public ResponseEntity<UserDTO> updateUser(@PathVariable final Long id,
                                              @RequestBody final UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    /**
     * Deletes a user.
     *
     * @param id user id
     * @return no content response
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('USER_DELETE')")
    public ResponseEntity<Void> deleteUser(@PathVariable final Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
