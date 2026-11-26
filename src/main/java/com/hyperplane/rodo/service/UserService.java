package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.UserRequestDTO;
import com.hyperplane.rodo.dto.UserResponseDTO;
import com.hyperplane.rodo.exception.BusinessRuleException;
import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.mapper.UserMapper;
import com.hyperplane.rodo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public Flux<UserResponseDTO> findAll() {
        return userRepository.findAll()
                .map(userMapper::toResponseDTO);
    }

    public Mono<UserResponseDTO> findById(String id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuário não encontrado: " + id)))
                .map(userMapper::toResponseDTO);
    }

    public Mono<UserResponseDTO> create(UserRequestDTO requestDTO) {
        return userRepository.findByEmail(requestDTO.email())
                .flatMap(existing -> Mono.<UserResponseDTO>error(new BusinessRuleException("Email já cadastrado: " + requestDTO.email())))
                .switchIfEmpty(Mono.defer(() -> Mono.just(userMapper.toEntity(requestDTO).toBuilder()
                                .passwordHash(passwordEncoder.encode(
                                        requestDTO.password() != null && !requestDTO.password().isBlank()
                                                ? requestDTO.password()
                                                : "Mudar@123"
                                ))
                                .build())
                        .flatMap(userRepository::save)
                        .map(userMapper::toResponseDTO)));
    }

    public Mono<UserResponseDTO> update(String id, UserRequestDTO requestDTO) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuário não encontrado: " + id)))
                .map(existing -> existing.toBuilder()
                        .name(requestDTO.name())
                        .email(requestDTO.email())
                        .phone(requestDTO.phone())
                        .role(requestDTO.role())
                        .active(requestDTO.active() != null ? requestDTO.active() : existing.isActive())
                        .passwordHash(requestDTO.password() != null && !requestDTO.password().isBlank()
                                ? passwordEncoder.encode(requestDTO.password())
                                : existing.getPasswordHash())
                        .updatedAt(Instant.now())
                        .build())
                .flatMap(userRepository::save)
                .map(userMapper::toResponseDTO);
    }

    public Mono<Void> delete(String id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuário não encontrado: " + id)))
                .flatMap(userRepository::delete);
    }
}
