package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.MotoboyRequestDTO;
import com.hyperplane.rodo.dto.MotoboyResponseDTO;
import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.mapper.MotoboyMapper;
import com.hyperplane.rodo.repository.MotoboyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static reactor.core.publisher.Mono.just;
import static reactor.core.publisher.Mono.justOrEmpty;

@Service
@Slf4j
@RequiredArgsConstructor
public class MotoboyService {

    private final MotoboyRepository motoboyRepository;
    private final MotoboyMapper motoboyMapper;

    public Flux<MotoboyResponseDTO> findAll() {
        return motoboyRepository.findAll()
                .map(motoboyMapper::toResponseDTO);
    }

    public Mono<MotoboyResponseDTO> findById(String id) {
        return justOrEmpty(id)
                .flatMap(motoboyRepository::findById)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Motoboy não encontrado: " + id)))
                .map(motoboyMapper::toResponseDTO);
    }

    public Mono<MotoboyResponseDTO> create(MotoboyRequestDTO requestDTO) {
        return just(requestDTO)
                .map(motoboyMapper::toEntity)
                .flatMap(motoboyRepository::save)
                .map(motoboyMapper::toResponseDTO);
    }

    public Mono<MotoboyResponseDTO> update(String id, MotoboyRequestDTO requestDTO) {
        return motoboyRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Motoboy não encontrado: " + id)))
                .map(existing -> existing.toBuilder()
                        .name(requestDTO.name())
                        .cpf(requestDTO.cpf())
                        .phone(requestDTO.phone())
                        .type(requestDTO.type())
                        .active(requestDTO.active() != null ? requestDTO.active() : existing.isActive())
                        .updatedAt(Instant.now())
                        .build())
                .flatMap(motoboyRepository::save)
                .map(motoboyMapper::toResponseDTO);
    }
}
