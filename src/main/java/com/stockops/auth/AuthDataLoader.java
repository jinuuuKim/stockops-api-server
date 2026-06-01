package com.stockops.auth;

import com.stockops.entity.Role;
import com.stockops.entity.User;
import com.stockops.repository.RoleRepository;
import com.stockops.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the default administrator account required for first-time access.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Component
public class AuthDataLoader implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "admin@stockops.com";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final List<SeedAccount> TEST_ACCOUNTS = List.of(
            new SeedAccount("manager@stockops.com", "StockOps Manager", "MANAGER"),
            new SeedAccount("staff@stockops.com", "StockOps Staff", "STAFF"),
            new SeedAccount("user@stockops.com", "StockOps User", "USER")
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String testAccountPassword;

    /**
     * Creates the authentication data loader.
     *
     * @param userRepository user repository
     * @param roleRepository role repository
     * @param passwordEncoder password encoder
     */
    public AuthDataLoader(final UserRepository userRepository,
                          final RoleRepository roleRepository,
                          final PasswordEncoder passwordEncoder,
                          @Value("${stockops.test-accounts.password:}") final String testAccountPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.testAccountPassword = testAccountPassword;
    }

    /**
     * Inserts the default administrator if it does not already exist.
     *
     * @param args application arguments
     */
    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        seedAccount(new SeedAccount(ADMIN_EMAIL, "StockOps Admin", ADMIN_ROLE), "admin123");

        if (!testAccountPassword.isBlank()) {
            for (SeedAccount account : TEST_ACCOUNTS) {
                seedAccount(account, testAccountPassword);
            }
        }
    }

    private void seedAccount(final SeedAccount account, final String password) {
        if (userRepository.existsByEmail(account.email())) {
            return;
        }

        final Role role = roleRepository.findByName(account.role())
                .orElseThrow(() -> new IllegalStateException("Missing role: " + account.role()));

        final User user = new User();
        user.setEmail(account.email());
        user.setPassword(passwordEncoder.encode(password));
        user.setName(account.name());
        user.setEnabled(true);
        user.setRole(role);

        userRepository.save(user);
    }

    private record SeedAccount(String email, String name, String role) {
    }
}
