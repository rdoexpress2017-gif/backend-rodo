package com.hyperplane.rodo.service;

import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.mapper.MotoboyMapper;
import com.hyperplane.rodo.model.Motoboy;
import com.hyperplane.rodo.repository.MotoboyRepository;
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
class MotoboyServiceTest {

    @Mock
    private MotoboyRepository motoboyRepository;

    @Mock
    private MotoboyMapper motoboyMapper;

    @InjectMocks
    private MotoboyService motoboyService;

    @Test
    @DisplayName("Should delete motoboy when id exists")
    void shouldDeleteMotoboyWhenIdExists() {
        final Motoboy existing = Motoboy.builder().id("m-1").name("Carlos Eduardo").build();
        when(motoboyRepository.findById("m-1")).thenReturn(Mono.just(existing));
        when(motoboyRepository.delete(existing)).thenReturn(Mono.empty());

        StepVerifier.create(motoboyService.delete("m-1"))
                .verifyComplete();

        verify(motoboyRepository).delete(existing);
    }

    @Test
    @DisplayName("Should emit ResourceNotFoundException when deleting non-existent motoboy")
    void shouldEmitResourceNotFoundWhenDeletingNonExistentMotoboy() {
        when(motoboyRepository.findById("m-404")).thenReturn(Mono.empty());

        StepVerifier.create(motoboyService.delete("m-404"))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }
}
