package com.corebank.gateway.config;

import com.corebank.domain.exception.InvalidAccessTokenException;
import com.corebank.domain.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;
  private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = authorizationHeader.substring(BEARER_PREFIX.length());

    try {
      UUID userId = jwtService.parseUserId(token);
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
      SecurityContextHolder.getContext().setAuthentication(authentication);
      filterChain.doFilter(request, response);
    } catch (InvalidAccessTokenException e) {
      restAuthenticationEntryPoint.commence(
          request, response, new InsufficientAuthenticationException(e.getMessage(), e));
    }
  }
}
