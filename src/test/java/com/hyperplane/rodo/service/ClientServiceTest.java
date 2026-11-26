package com.hyperplane.rodo.service;

import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.mapper.ClientMapper;
import com.hyperplane.rodo.model.Client;
import com.hyperplane.rodo.repository.ClientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientMapper clientMapper;

    @InjectMocks
    private ClientService clientService;

    @Test
    @DisplayName("Should delete client when id exists")
    void shouldDeleteClientWhenIdExists() {
        final Client existing = Client.builder().id("c-1").name("INCLOSET").build();
        when(clientRepository.findById("c-1")).thenReturn(Mono.just(existing));
        when(clientRepository.delete(existing)).thenReturn(Mono.empty());

        StepVerifier.create(clientService.delete("c-1"))
                .verifyComplete();

        verify(clientRepository).delete(existing);
    }

    @Test
    @DisplayName("Should emit ResourceNotFoundException when deleting non-existent client")
    void shouldEmitResourceNotFoundWhenDeletingNonExistentClient() {
        when(clientRepository.findById("c-404")).thenReturn(Mono.empty());

        StepVerifier.create(clientService.delete("c-404"))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }
}
