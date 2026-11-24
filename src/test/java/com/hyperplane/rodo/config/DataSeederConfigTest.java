package com.hyperplane.rodo.config;

import com.hyperplane.rodo.model.Client;
import com.hyperplane.rodo.model.Motoboy;
import com.hyperplane.rodo.model.Order;
import com.hyperplane.rodo.model.SystemSettings;
import com.hyperplane.rodo.model.User;
import com.hyperplane.rodo.repository.ClientRepository;
import com.hyperplane.rodo.repository.MotoboyRepository;
import com.hyperplane.rodo.repository.OrderRepository;
import com.hyperplane.rodo.repository.SystemSettingsRepository;
import com.hyperplane.rodo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederConfigTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SystemSettingsRepository settingsRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private MotoboyRepository motoboyRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Should ONLY seed masterSeed (admin@rodo.com) when SEED_SAMPLE_DATA flag is false")
    void shouldOnlySeedMasterUserWhenTestDataFlagIsFalse() {
        // Arrange
        final DataSeederConfig seeder = new DataSeederConfig(
                userRepository,
                settingsRepository,
                clientRepository,
                motoboyRepository,
                orderRepository,
                passwordEncoder,
                false
        );

        when(passwordEncoder.encode("Admin@123")).thenReturn("hashed-admin");
        when(userRepository.findByEmail("admin@rodo.com")).thenReturn(Mono.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            final User u = inv.getArgument(0);
            return Mono.just(u.toBuilder().id("master-1").build());
        });

        // Act & Assert
        StepVerifier.create(seeder.executeSeed())
                .verifyComplete();

        verify(userRepository, times(1)).findByEmail("admin@rodo.com");
        verify(userRepository, never()).findByEmail("atendente@rodo.com");
        verify(userRepository, times(1)).save(any(User.class));
        verify(settingsRepository, never()).findById(anyString());
        verify(settingsRepository, never()).save(any(SystemSettings.class));
        verify(clientRepository, never()).count();
        verify(clientRepository, never()).save(any(Client.class));
        verify(motoboyRepository, never()).save(any(Motoboy.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should seed masterSeed AND all sample data (atendente, settings, clients, motoboys, orders) when SEED_SAMPLE_DATA is true")
    void shouldSeedOperationalSampleDataWhenTestDataFlagIsTrue() {
        // Arrange
        final DataSeederConfig seeder = new DataSeederConfig(
                userRepository,
                settingsRepository,
                clientRepository,
                motoboyRepository,
                orderRepository,
                passwordEncoder,
                true
        );

        final User masterUser = User.builder().id("master-1").email("admin@rodo.com").build();
        when(userRepository.findByEmail("admin@rodo.com")).thenReturn(Mono.just(masterUser));
        when(userRepository.findByEmail("atendente@rodo.com")).thenReturn(Mono.just(masterUser));
        when(settingsRepository.findById(SystemSettings.GLOBAL_CONFIG_ID))
                .thenReturn(Mono.just(SystemSettings.builder().id(SystemSettings.GLOBAL_CONFIG_ID).build()));
        when(clientRepository.count()).thenReturn(Mono.just(0L));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            final Client c = inv.getArgument(0);
            return Mono.just(c.toBuilder().id("c-1").build());
        });
        when(motoboyRepository.save(any(Motoboy.class))).thenAnswer(inv -> {
            final Motoboy m = inv.getArgument(0);
            return Mono.just(m.toBuilder().id("m-1").build());
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Act & Assert
        StepVerifier.create(seeder.executeSeed())
                .verifyComplete();

        verify(userRepository, times(1)).findByEmail("atendente@rodo.com");
        verify(settingsRepository, times(1)).findById(SystemSettings.GLOBAL_CONFIG_ID);
        verify(clientRepository, times(2)).save(any(Client.class));
        verify(motoboyRepository, times(2)).save(any(Motoboy.class));
        verify(orderRepository, times(25)).save(any(Order.class));
    }
}
