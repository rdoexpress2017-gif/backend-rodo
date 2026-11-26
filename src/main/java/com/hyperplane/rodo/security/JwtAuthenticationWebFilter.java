package com.hyperplane.rodo.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationWebFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationWebFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(header -> header.startsWith(BEARER_PREFIX))
                .map(header -> header.substring(BEARER_PREFIX.length()))
                .map(token -> jwtTokenProvider.parseClaims(token)
                        .map(claims -> chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken(
                                                claims.getSubject(),
                                                null,
                                                List.of(new SimpleGrantedAuthority(claims.get("role", String.class)))
                                        )
                                )))
                        .getOrElse(() -> chain.filter(exchange)))
                .orElseGet(() -> chain.filter(exchange));
    }
}
