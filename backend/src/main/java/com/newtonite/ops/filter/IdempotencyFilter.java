package com.newtonite.ops.filter;

import com.newtonite.ops.model.entity.IdempotencyRecord;
import com.newtonite.ops.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Filter that intercepts HTTP requests carrying an 'Idempotency-Key' header.
 * If a repeated request is detected, the cached response is served directly without
 * re-executing business transactions.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String HEADER_CACHE_LOOKUP = "X-Cache-Lookup";

    private final IdempotencyService idempotencyService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String idempotencyKey = request.getHeader(HEADER_IDEMPOTENCY_KEY);
        String method = request.getMethod();

        // Idempotency applies strictly to mutating HTTP methods carrying the header
        boolean isMutating = "POST".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty() || !isMutating) {
            filterChain.doFilter(request, response);
            return;
        }

        idempotencyKey = idempotencyKey.trim();
        log.info("Processing request with Idempotency-Key: {} on {} {}", idempotencyKey, method, request.getRequestURI());

        // Check if cached response exists
        Optional<IdempotencyRecord> cachedOpt = idempotencyService.findRecord(idempotencyKey);
        if (cachedOpt.isPresent()) {
            IdempotencyRecord cached = cachedOpt.get();
            log.info("Cache HIT for Idempotency-Key {}. Returning cached status {}", idempotencyKey, cached.getStatusCode());

            response.setStatus(cached.getStatusCode());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader(HEADER_CACHE_LOOKUP, "HIT");
            response.setHeader(HEADER_IDEMPOTENCY_KEY, idempotencyKey);

            if (cached.getResponseBody() != null) {
                response.getWriter().write(cached.getResponseBody());
            }
            response.getWriter().flush();
            return;
        }

        // Cache MISS: Wrap request and response to record payload and output
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            int statusCode = wrappedResponse.getStatus();
            byte[] responseBytes = wrappedResponse.getContentAsByteArray();
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);

            // Compute hash of request to bind key to this exact request
            String requestHash = computeHash(request.getRequestURI(), wrappedRequest.getCachedBody());

            // Save record if request completed (e.g. 2xx success or 4xx client errors)
            if (statusCode < 500) {
                log.info("Recording new idempotent response for key: {}, status: {}", idempotencyKey, statusCode);
                idempotencyService.saveRecord(idempotencyKey, null, requestHash, statusCode, responseBody);
            }

            wrappedResponse.setHeader(HEADER_CACHE_LOOKUP, "MISS");
            wrappedResponse.setHeader(HEADER_IDEMPOTENCY_KEY, idempotencyKey);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private String computeHash(String uri, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(uri.getBytes(StandardCharsets.UTF_8));
            if (body != null && body.length > 0) {
                digest.update(body);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            return String.valueOf(uri.hashCode());
        }
    }
}
