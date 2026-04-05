package org.example.helptreeservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                Long userId = claims.get("userId", Long.class);
                String role = claims.get("role", String.class);
                String email = claims.getSubject();

                if (userId != null) {
                    request.setAttribute("X-User-Id", userId.toString());
                    request.setAttribute("X-User-Role", role != null ? role : "USER");
                    request.setAttribute("X-User-Email", email != null ? email : "");

                    response.setHeader("X-User-Id", userId.toString());
                    response.setHeader("X-User-Role", role != null ? role : "USER");
                    response.setHeader("X-User-Email", email != null ? email : "");
                }
            } catch (Exception e) {
                // Token invalid or expired - continue without setting headers
            }
        }

        filterChain.doFilter(request, response);
    }
}
