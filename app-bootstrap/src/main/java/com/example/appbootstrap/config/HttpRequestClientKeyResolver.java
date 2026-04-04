package com.example.appbootstrap.config;

import com.example.moduleidentity.port.ClientKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class HttpRequestClientKeyResolver implements ClientKeyResolver {

  @Override
  public String resolveClientKey() {
    var attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
      return null;
    }

    HttpServletRequest request = servletAttributes.getRequest();
    //lấy ip khi đi qua các proxy như nginx
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwardedFor)) {
      String first = forwardedFor.split(",")[0].trim();
      if (StringUtils.hasText(first)) {
        return first;
      }
    }

    //lấy ip khi đi qua proxy apache hoặc nginx
    String realIp = request.getHeader("X-Real-IP");
    if (StringUtils.hasText(realIp)) {
      return realIp.trim();
    }
    //nếu không có header nào thì lấy ip trực tiếp
    return request.getRemoteAddr();
  }
}
