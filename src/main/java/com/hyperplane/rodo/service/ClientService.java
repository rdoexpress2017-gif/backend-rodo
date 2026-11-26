package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.ClientRequestDTO;
import com.hyperplane.rodo.dto.ClientResponseDTO;
import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.mapper.ClientMapper;
import com.hyperplane.rodo.repository.ClientRepository;
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
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;

    public Flux<ClientResponseDTO> findAll() {
        return clientRepository.findAll()
                .map(clientMapper::toResponseDTO);
    }

    public Mono<ClientResponseDTO> findById(String id) {
        return justOrEmpty(id)
                .flatMap(clientRepository::findById)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cliente não encontrado: " + id)))
                .map(clientMapper::toResponseDTO);
    }

    public Mono<ClientResponseDTO> create(ClientRequestDTO requestDTO) {
        return just(requestDTO)
                .map(clientMapper::toEntity)
                .flatMap(clientRepository::save)
                .map(clientMapper::toResponseDTO);
    }

    public Mono<ClientResponseDTO> update(String id, ClientRequestDTO requestDTO) {
        return clientRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cliente não encontrado: " + id)))
                .map(existing -> existing.toBuilder()
                        .name(requestDTO.name())
                        .document(requestDTO.document())
                        .email(requestDTO.email())
                        .phone(requestDTO.phone())
                        .baseAddress(clientMapper.toAddress(requestDTO.baseAddress()))
                        .billingCycle(clientMapper.toBillingCycle(requestDTO.billingCycle()))
                        .active(requestDTO.active() != null ? requestDTO.active() : existing.isActive())
                        .updatedAt(Instant.now())
                        .build())
                .flatMap(clientRepository::save)
                .map(clientMapper::toResponseDTO);
    }

    public Mono<Void> delete(String id) {
        return clientRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cliente não encontrado: " + id)))
                .flatMap(clientRepository::delete);
    }
}
