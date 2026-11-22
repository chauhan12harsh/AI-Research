package com.harsh.research.security;

import com.harsh.research.user.User;
import com.harsh.research.user.UserRepository;
import com.harsh.research.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {


        String header = request.getHeader("Authorization");

        if(header!=null && header.startsWith("Bearer ")){

            String token = header.substring(7);

            Optional<AuthPrincipal> principal = jwtService.parse(token);

            if (principal.isPresent()) {
                AuthPrincipal tokenPrincipal = principal.get();
                User user = userRepository.findById(tokenPrincipal.userId()).orElse(null);

                // Resolve authorization from current database state so a suspended
                // account or changed role takes effect without waiting for token expiry.
                if (user == null || user.getStatus() != UserStatus.ACTIVE
                        || !user.getEmail().equalsIgnoreCase(tokenPrincipal.email())
                        || user.getTokenVersion() != tokenPrincipal.tokenVersion()) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                AuthPrincipal p = new AuthPrincipal(
                        user.getId(), user.getEmail(), user.getRole().name(), user.getTokenVersion()
                );

                var authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + p.role())
                );

                var authentication = new UsernamePasswordAuthenticationToken(
                        p, null, authorities
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request,response);
    }
}
