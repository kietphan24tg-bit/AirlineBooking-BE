package com.example.appbootstrap.config;

import com.example.moduleidentity.port.ClientKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class HttpRequestClientKeyResolver implements ClientKeyResolver {

  private static final Logger log = LoggerFactory.getLogger(HttpRequestClientKeyResolver.class);

  private final List<IpAddressMatcher> trustedProxyMatchers;

  public HttpRequestClientKeyResolver(ClientIpProperties clientIpProperties) {
    this.trustedProxyMatchers =
        clientIpProperties.trustedProxies() == null
            ? List.of()
            : clientIpProperties.trustedProxies().stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(StringUtils::hasText)
                .map(this::toIpMatcher)
                .filter(Objects::nonNull)
                .toList();
  }

  @Override
  public String resolveClientKey() {
    var attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
      return null;
    }

    HttpServletRequest request = servletAttributes.getRequest();
    String remoteAddr = request.getRemoteAddr();
    if (isTrustedProxy(remoteAddr)) {
      // chỉ tin header chuyển tiếp khi request đến từ proxy đã được trust
      String forwardedFor = request.getHeader("X-Forwarded-For");
      if (StringUtils.hasText(forwardedFor)) {
        String first = forwardedFor.split(",")[0].trim();
        if (StringUtils.hasText(first)) {
          return first;
        }
      }

      String realIp = request.getHeader("X-Real-IP");
      if (StringUtils.hasText(realIp)) {
        return realIp.trim();
      }
    }

    // nếu không có header nào thì lấy ip trực tiếp
    return remoteAddr;
  }

  private boolean isTrustedProxy(String remoteAddr) {
    if (!StringUtils.hasText(remoteAddr) || trustedProxyMatchers.isEmpty()) {
      return false;
    }
    return trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(remoteAddr));
  }

  private IpAddressMatcher toIpMatcher(String rawValue) {
    try {
      return new IpAddressMatcher(rawValue);
    } catch (IllegalArgumentException ex) {
      log.warn("Ignoring invalid trusted proxy IP/CIDR entry: {}", rawValue);
      return null;
    }
  }
}
