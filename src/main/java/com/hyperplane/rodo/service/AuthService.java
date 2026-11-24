package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.AuthLoginRequestDTO;
import com.hyperplane.rodo.dto.AuthLoginResponseDTO;
import com.hyperplane.rodo.repository.UserRepository;
import com.hyperplane.rodo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public Mono<AuthLoginResponseDTO> login(AuthLoginRequestDTO requestDTO) {
        return userRepository.findByEmail(requestDTO.email())
                .filter(user -> user.isActive() && passwordEncoder.matches(requestDTO.password(), user.getPasswordHash()))
                .switchIfEmpty(Mono.error(new BadCredentialsException("Usuário ou senha incorretos")))
                .map(user -> new AuthLoginResponseDTO(
                        jwtTokenProvider.generateToken(user),
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getRole()
                ));
    }
}
