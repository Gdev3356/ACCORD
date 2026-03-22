package com.main.accord.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (!jwtService.isAccessTokenValid(token)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parseAccessToken(token);
            UUID   userId = UUID.fromString(claims.getSubject());
            String email  = claims.get("email", String.class);
            boolean isAdmin = Boolean.TRUE.equals(claims.get("admin", Boolean.class));

            AccordPrincipal principal = new AccordPrincipal(userId, email, isAdmin);
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, Collections.emptyList()
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception ignored) {}

        chain.doFilter(request, response);
    }
}