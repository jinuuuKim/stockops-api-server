package com.stockops.entity;

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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

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
@Data
@Entity
@Table(name = "users")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * User-specific scope assignments merged with role scope assignments.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_scope_assignments", joinColumns = @JoinColumn(name = "user_id"))
    private Set<ScopeAssignment> scopeAssignments = new LinkedHashSet<>();
}
