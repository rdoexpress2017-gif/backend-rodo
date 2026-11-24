package com.hyperplane.rodo.config;

import com.hyperplane.rodo.model.Address;
import com.hyperplane.rodo.model.BillingCycle;
import com.hyperplane.rodo.model.Client;
import com.hyperplane.rodo.model.Motoboy;
import com.hyperplane.rodo.model.Order;
import com.hyperplane.rodo.model.SystemSettings;
import com.hyperplane.rodo.model.User;
import com.hyperplane.rodo.model.enums.BillingCycleType;
import com.hyperplane.rodo.model.enums.DayOfWeekEnum;
import com.hyperplane.rodo.model.enums.MotoboyType;
import com.hyperplane.rodo.model.enums.OrderStatus;
import com.hyperplane.rodo.model.enums.UserRole;
import com.hyperplane.rodo.repository.ClientRepository;
import com.hyperplane.rodo.repository.MotoboyRepository;
import com.hyperplane.rodo.repository.OrderRepository;
import com.hyperplane.rodo.repository.SystemSettingsRepository;
import com.hyperplane.rodo.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

@Configuration
@Slf4j
public class DataSeederConfig {

    private final UserRepository userRepository;
    private final SystemSettingsRepository settingsRepository;
    private final ClientRepository clientRepository;
    private final MotoboyRepository motoboyRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedSampleData;

    public DataSeederConfig(
            UserRepository userRepository,
            SystemSettingsRepository settingsRepository,
            ClientRepository clientRepository,
            MotoboyRepository motoboyRepository,
            OrderRepository orderRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.sample-data:false}") boolean seedSampleData
    ) {
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.clientRepository = clientRepository;
        this.motoboyRepository = motoboyRepository;
        this.orderRepository = orderRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedSampleData = seedSampleData;
    }

    @Bean
    public CommandLineRunner seedDatabase() {
        return args -> executeSeed()
                .doOnSuccess(v -> log.info(
                        "Seed de inicialização verificado com sucesso (SEED_SAMPLE_DATA={}).",
                        seedSampleData
                ))
                .doOnError(err -> log.warn("Aviso durante seed inicial: {}", err.getMessage()))
                .subscribe();
    }

    public Mono<Void> executeSeed() {
        return Mono.just(Instant.now())
                .doOnNext(time -> log.info("Seed iniciado em: {}", time))
                .flatMap(time -> userRepository.findByEmail("admin@rodo.com"))
                .switchIfEmpty(Mono.defer(() -> userRepository.save(User.builder()
                        .name("Rodrigo (Master)")
                        .email("admin@rodo.com")
                        .passwordHash(passwordEncoder.encode("Admin@123"))
                        .phone("+55 31 99999-0001")
                        .role(UserRole.MASTER)
                        .active(true)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build())))
                .filter(user -> seedSampleData)
                .flatMap(user -> seedRemainingSampleData(Instant.now()));
    }

    private Mono<Void> seedRemainingSampleData(Instant now) {
        final Mono<User> atendenteSeed = userRepository.findByEmail("atendente@rodo.com")
                .switchIfEmpty(Mono.defer(() -> userRepository.save(User.builder()
                        .name("Maria Atendente")
                        .email("atendente@rodo.com")
                        .passwordHash(passwordEncoder.encode("Atendente@123"))
                        .phone("+55 31 98888-0002")
                        .role(UserRole.ATENDENTE)
                        .active(true)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())));

        final Mono<SystemSettings> settingsSeed = settingsRepository.findById(SystemSettings.GLOBAL_CONFIG_ID)
                .switchIfEmpty(Mono.defer(() -> settingsRepository.save(SystemSettings.builder()
                        .id(SystemSettings.GLOBAL_CONFIG_ID)
                        .pricePerKm(new BigDecimal("2.50"))
                        .baseRate(new BigDecimal("10.00"))
                        .defaultMotoboyCommissionPercentage(80.0)
                        .updatedByUserId("SYSTEM_SEED")
                        .updatedAt(now)
                        .build())));

        return Mono.when(atendenteSeed, settingsSeed)
                .then(Mono.defer(this::seedOperationalSampleData));
    }

    private Mono<Void> seedOperationalSampleData() {
        return clientRepository.count()
                .filter(count -> count == 0)
                .flatMap(ignored -> {
                    final Instant now = Instant.now();
                    final Client c1 = Client.builder()
                            .name("Farmácia DrogaRaia Savassi")
                            .document("12.345.678/0001-90")
                            .email("contato@drogaraia.com.br")
                            .phone("(31) 3222-1100")
                            .baseAddress(Address.builder()
                                    .street("Av. do Contorno")
                                    .number("6000")
                                    .neighborhood("Savassi")
                                    .city("Belo Horizonte")
                                    .state("MG")
                                    .zipCode("30110-042")
                                    .build())
                            .billingCycle(BillingCycle.builder()
                                    .type(BillingCycleType.DAILY)
                                    .build())
                            .active(true)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    final Client c2 = Client.builder()
                            .name("Restaurante Sabor Mineiro")
                            .document("98.765.432/0001-10")
                            .email("financeiro@sabormineiro.com.br")
                            .phone("(31) 3344-5566")
                            .baseAddress(Address.builder()
                                    .street("Rua Pedra Bonita")
                                    .number("878")
                                    .neighborhood("Barroca")
                                    .city("Belo Horizonte")
                                    .state("MG")
                                    .zipCode("30431-030")
                                    .build())
                            .billingCycle(BillingCycle.builder()
                                    .type(BillingCycleType.WEEKLY)
                                    .startDayOfWeek(DayOfWeekEnum.SEGUNDA)
                                    .endDayOfWeek(DayOfWeekEnum.SEXTA)
                                    .build())
                            .active(true)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    final Motoboy m1 = Motoboy.builder()
                            .name("Carlos Eduardo Silva")
                            .cpf("111.222.333-44")
                            .phone("(31) 99111-2233")
                            .type(MotoboyType.FIXO)
                            .active(true)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    final Motoboy m2 = Motoboy.builder()
                            .name("Marcos Vinícius Souza")
                            .cpf("555.666.777-88")
                            .phone("(31) 99444-5566")
                            .type(MotoboyType.TERCEIRIZADO)
                            .active(true)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    return Mono.zip(
                            clientRepository.save(c1),
                            clientRepository.save(c2),
                            motoboyRepository.save(m1),
                            motoboyRepository.save(m2),
                            userRepository.findByEmail("admin@rodo.com")
                    ).flatMap(tuple -> {
                        final Client savedC1 = tuple.getT1();
                        final Client savedC2 = tuple.getT2();
                        final Motoboy savedM1 = tuple.getT3();
                        final Motoboy savedM2 = tuple.getT4();
                        final User master = tuple.getT5();

                        final List<Order> sampleOrders = IntStream.rangeClosed(1, 25)
                                .mapToObj(i -> {
                                    final boolean even = i % 2 == 0;
                                    final OrderStatus st = i <= 8 ? OrderStatus.ENTREGUE : (i <= 15 ? OrderStatus.COLETADO : OrderStatus.PENDENTE);
                                    final BigDecimal val = even ? new BigDecimal("35.00") : new BigDecimal("25.00");
                                    final BigDecimal comm = even ? new BigDecimal("28.00") : new BigDecimal("20.00");
                                    return Order.builder()
                                            .orderNumber(1000L + i)
                                            .clientId(even ? savedC2.getId() : savedC1.getId())
                                            .motoboyId(even ? savedM2.getId() : savedM1.getId())
                                            .requesterName(even ? "MARIANA" : "A. Junqueira")
                                            .createdByUserId(master.getId())
                                            .originAddress(even ? savedC2.getBaseAddress() : savedC1.getBaseAddress())
                                            .destinationAddress(Address.builder()
                                                    .street("Rua Pedra Bonita")
                                                    .number(String.valueOf(800 + i))
                                                    .neighborhood("Barroca")
                                                    .city("Belo Horizonte")
                                                    .state("MG")
                                                    .zipCode("30431-030")
                                                    .build())
                                            .routeDistanceKm(5.0 + (i % 7))
                                            .amount(val)
                                            .motoboyCommissionAmount(comm)
                                            .status(st)
                                            .createdAt(now.minus(25 - i, ChronoUnit.HOURS))
                                            .collectedAt(st != OrderStatus.PENDENTE ? now.minus(24 - i, ChronoUnit.HOURS) : null)
                                            .deliveredAt(st == OrderStatus.ENTREGUE ? now.minus(23 - i, ChronoUnit.HOURS) : null)
                                            .updatedAt(now)
                                            .build();
                                })
                                .toList();

                        return Flux.fromIterable(sampleOrders)
                                .flatMap(orderRepository::save)
                                .then();
                    });
                });
    }
}
