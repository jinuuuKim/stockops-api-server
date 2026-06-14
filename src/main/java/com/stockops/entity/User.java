package com.stockops.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.stockops.security.ScopeAssignment;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User account entity.
 * Stores local authentication credentials and the assigned role.
 *
 * @author StockOps Team
 * @since 1.0
 * @see Role
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "phone", length = 50)
    private String phone;

    /**
     * Owning store for store-role users (STORE_MANAGER/STORE_STAFF); null for other roles.
     * Stored as a plain id to keep User lightweight and avoid eager store loading.
     */
    @Column(name = "store_id")
    private Long storeId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * User-specific scope assignments merged with role scope assignments.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_scope_assignments", joinColumns = @JoinColumn(name = "user_id"))
    private Set<ScopeAssignment> scopeAssignments = new LinkedHashSet<>();

    public User() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    @JsonIgnore
    public String getPassword() {
        return this.password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getPhone() {
        return this.phone;
    }

    public void setPhone(final String phone) {
        this.phone = phone;
    }

    public Long getStoreId() {
        return this.storeId;
    }

    public void setStoreId(final Long storeId) {
        this.storeId = storeId;
    }

    public Role getRole() {
        return this.role;
    }

    public void setRole(final Role role) {
        this.role = role;
    }

    public Set<ScopeAssignment> getScopeAssignments() {
        return this.scopeAssignments;
    }

    public void setScopeAssignments(final Set<ScopeAssignment> scopeAssignments) {
        this.scopeAssignments = scopeAssignments;
    }
}
