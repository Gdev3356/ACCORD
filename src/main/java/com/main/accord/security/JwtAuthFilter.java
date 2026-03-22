package com.main.accord.security;

import com.main.accord.domain.account.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.oauth2.jwt.*;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;   // configured in SecurityConfig

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

        try {
            String    token = header.substring(7);
            Jwt       jwt   = jwtDecoder.decode(token);

            // Supabase puts the user UUID in the "sub" claim
            UUID   userId = UUID.fromString(jwt.getSubject());
            String email  = jwt.getClaimAsString("email");

            AccordPrincipal principal = new AccordPrincipal(userId, email);
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, Collections.emptyList()
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (JwtException e) {
            // Invalid / expired token — let the request through as anonymous.
            // Endpoints that need auth will return 401 via the security filter chain.
        }

        chain.doFilter(request, response);
    }
}