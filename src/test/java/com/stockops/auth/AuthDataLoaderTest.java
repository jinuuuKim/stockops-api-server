package com.stockops.auth;

import com.stockops.entity.Role;
import com.stockops.entity.User;
import com.stockops.repository.RoleRepository;
import com.stockops.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthDataLoaderTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsAdminAndRoleTestAccountsWhenPasswordIsConfigured() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName(anyString())).thenAnswer(invocation -> Optional.of(role(invocation.getArgument(0))));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "encoded-" + invocation.getArgument(0));
        AuthDataLoader loader = new AuthDataLoader(userRepository, roleRepository, passwordEncoder, "test-password");

        loader.run(null);

        ArgumentCaptor<User> users = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(4)).save(users.capture());

        List<User> created = users.getAllValues();
        assertThat(created).extracting(User::getEmail)
                .containsExactly("admin@stockops.com", "manager@stockops.com", "staff@stockops.com", "user@stockops.com");
        assertThat(created).extracting(user -> user.getRole().getName())
                .containsExactly("ADMIN", "MANAGER", "STAFF", "USER");
        assertThat(created).allMatch(User::isEnabled);
    }

    @Test
    void skipsRoleTestAccountsWhenPasswordIsBlank() {
        when(userRepository.existsByEmail("admin@stockops.com")).thenReturn(false);
        when(roleRepository.findByName(anyString())).thenAnswer(invocation -> Optional.of(role(invocation.getArgument(0))));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "encoded-" + invocation.getArgument(0));
        AuthDataLoader loader = new AuthDataLoader(userRepository, roleRepository, passwordEncoder, "");

        loader.run(null);

        ArgumentCaptor<User> users = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(users.capture());
        assertThat(users.getValue().getEmail()).isEqualTo("admin@stockops.com");
        verify(userRepository, never()).existsByEmail("manager@stockops.com");
    }

    @Test
    void doesNotOverwriteExistingAccounts() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        AuthDataLoader loader = new AuthDataLoader(userRepository, roleRepository, passwordEncoder, "test-password");

        loader.run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    private static Role role(final String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
