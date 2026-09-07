package com.lmguard.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmguard.common.ApiResponse;
import com.lmguard.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns the standard error envelope for unauthenticated requests.
 *
 * <p>Without this Spring Security writes an empty 401 body, which would be the one response
 * in the API that does not match the documented shape.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(
                "Authentication required. Send a valid 'Authorization: Bearer <token>' header.",
                ErrorCode.UNAUTHORIZED.name()));
    }
}
