package com.bank.fintechbank.config;

import com.bank.fintechbank.model.Customer;
import com.bank.fintechbank.repository.CustomerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Component
public class TokenAuthFilter extends OncePerRequestFilter {
    @Autowired private CustomerRepository customers;
    @Value("${cema.demo.enabled:false}") private boolean demoEnabled;

    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() < 512) {
            String token = header.substring(7);
            Customer c = customers.findByCurrentSessionToken(hash(token)).orElse(null);
            if (c != null && (demoEnabled || !c.getUsername().startsWith("demo_"))
                    && !c.isAccountLocked() && c.getSessionExpiresAt() != null && c.getSessionExpiresAt().isAfter(LocalDateTime.now())) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    c.getUsername(), null, List.of(new SimpleGrantedAuthority(c.getRole()))));
            }
        }
        chain.doFilter(request, response);
    }
}
