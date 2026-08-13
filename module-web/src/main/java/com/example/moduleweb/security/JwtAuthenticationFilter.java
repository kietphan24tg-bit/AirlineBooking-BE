package com.example.moduleweb.security;

import com.example.moduleidentity.application.security.AppUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Not a servlet {@code @Component}; built in {@link
 * com.example.moduleweb.config.SecurityConfig}.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;
  private final UserDetailsService userDetailsService;

  public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
    this.jwtService = jwtService;
    this.userDetailsService = userDetailsService;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String token = extractToken(request);

    if (token == null) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      String email = jwtService.extractEmail(token);

      // Chỉ xác thực nếu chưa có authentication trong context
      if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

        // Chỉ chấp nhận access token, không cho dùng refresh token để gọi API
        if (!jwtService.isAccessToken(token)) {
          log.warn("Rejected non-access token for: {}", email);
          filterChain.doFilter(request, response);
          return;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        if (isTokenInvalidated(userDetails, token)) {
          log.warn("Rejected invalidated access token for: {}", email);
          filterChain.doFilter(request, response);
          return;
        }

        if (jwtService.isTokenValid(token, userDetails.getUsername())) {
          // authentication object: mark user as authenticated
          UsernamePasswordAuthenticationToken authToken =
              new UsernamePasswordAuthenticationToken(
                  userDetails, null, userDetails.getAuthorities());
          // set details: ip address, session id, etc.
          authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          // set authentication in security context: spring security know this request authenticated
          SecurityContextHolder.getContext().setAuthentication(authToken);
        }
      }
    } catch (Exception e) {
      log.warn("Invalid JWT: {}", e.getMessage());
      // Không throw exception — để filter chain tiếp tục, SecurityConfig sẽ trả 401
    }
    // cho req đi tiếp
    filterChain.doFilter(request, response);
  }

  private String extractToken(HttpServletRequest request) {
    String header = request.getHeader(AUTH_HEADER);
    if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }

  private boolean isTokenInvalidated(UserDetails userDetails, String token) {
    if (!(userDetails instanceof AppUserPrincipal principal)) {
      return false;
    }

    OffsetDateTime tokenInvalidBefore = principal.getTokenInvalidBefore();
    if (tokenInvalidBefore == null) {
      return false;
    }

    OffsetDateTime issuedAt = jwtService.extractIssuedAt(token);
    return issuedAt.isBefore(tokenInvalidBefore);
  }
}
