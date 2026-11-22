package com.harsh.research.security;

import com.harsh.research.common.ApiError;
import com.harsh.research.common.ApiResponse;
import com.harsh.research.common.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1";
    private static final int MAX_BUCKETS = 10_000;
    private static final Bucket OVERFLOW_BUCKET = newBucket(120, Duration.ofMinutes(1));

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Limit limit = resolveLimit(request);
        if (limit != null) {
            String key = limit.category() + ":" + resolveKey(request);
            Bucket bucket = bucketFor(key, limit);

            if (!bucket.tryConsume(1)) {
                writeRateLimited(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket bucketFor(String key, Limit limit) {
        Bucket existing = buckets.get(key);
        if (existing != null) {
            return existing;
        }

        synchronized (buckets) {
            existing = buckets.get(key);
            if (existing != null) {
                return existing;
            }
            if (buckets.size() >= MAX_BUCKETS) {
                // Bound memory use. Under extreme cardinality, excess callers
                // share a conservative fallback bucket instead of creating keys.
                return OVERFLOW_BUCKET;
            }

            Bucket created = newBucket(limit.capacity(), limit.period());
            buckets.put(key, created);
            return created;
        }
    }

    private static Bucket newBucket(int capacity, Duration period) {
        Bandwidth bandwidth = Bandwidth.classic(
                capacity,
                Refill.intervally(capacity, period)
        );
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private record Limit(String category, int capacity, Duration period) {}

    private void writeRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.RATE_LIMITED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String traceId = UUID.randomUUID().toString();
        ApiError error = ApiError.of(
                String.valueOf(ErrorCode.RATE_LIMITED),
                "Too many requests"
        );

        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error(error, traceId))
        );
    }

    private Limit resolveLimit(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (matches(method, path, HttpMethod.POST, API_PREFIX + "/auth/login")) {
            return new Limit("login", 5, Duration.ofMinutes(1));
        }
        if (matches(method, path, HttpMethod.POST, API_PREFIX + "/auth/register")) {
            return new Limit("register", 3, Duration.ofMinutes(1));
        }
        if (matches(method, path, HttpMethod.POST,
                API_PREFIX + "/workspaces/*/ask")) {
            return new Limit("rag", 20, Duration.ofMinutes(1));
        }
        if (path.startsWith(API_PREFIX)) {
            return new Limit("general", 120, Duration.ofMinutes(1));
        }
        return null;
    }

    private boolean matches(
            String actualMethod,
            String actualPath,
            HttpMethod method,
            String pattern
    ) {
        return actualMethod.equalsIgnoreCase(method.name())
                && pathMatcher.match(pattern, actualPath);
    }

    private String resolveKey(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Optional<AuthPrincipal> principal = jwtService.parse(header.substring(7));
            if (principal.isPresent()) {
                return "user:" + principal.get().userId();
            }
        }

        // Do not trust arbitrary X-Forwarded-For values: clients can spoof them.
        // Configure the servlet container's trusted-proxy handling if deployed
        // behind a reverse proxy.
        return "ip:" + request.getRemoteAddr();
    }
}
