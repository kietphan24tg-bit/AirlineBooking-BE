package com.example.appbootstrap.api;

import com.example.appbootstrap.exception.ApiErrorResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.ObjectMapper;

@ControllerAdvice(annotations = {RestController.class, Controller.class})
public class ApiSuccessResponseBodyAdvice implements ResponseBodyAdvice<Object> {

  private static final String DEFAULT_SUCCESS_MESSAGE = "Operation successful";
  private final ObjectMapper objectMapper;

  public ApiSuccessResponseBodyAdvice(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return !ApiSuccessResponse.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (shouldSkip(body, request, response)) {
      return body;
    }

    ApiSuccessResponse<Object> wrappedBody =
        ApiSuccessResponse.of(body, resolveMessage(returnType));
    if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
      return objectMapper.writeValueAsString(wrappedBody);
    }
    return wrappedBody;
  }

  private boolean shouldSkip(Object body, ServerHttpRequest request, ServerHttpResponse response) {
    if (body == null && response instanceof ServletServerHttpResponse servletResponse) {
      int status = servletResponse.getServletResponse().getStatus();
      if (status == HttpStatus.NO_CONTENT.value() || status == HttpStatus.NOT_MODIFIED.value()) {
        return true;
      }
    }

    if (body instanceof ApiSuccessResponse
        || body instanceof ApiErrorResponse
        || body instanceof byte[]
        || body instanceof String) {
      return false;
    }

    String path = request.getURI().getPath();
    return path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/actuator");
  }

  private String resolveMessage(MethodParameter returnType) {
    ApiSuccessMessage methodAnnotation =
        AnnotatedElementUtils.findMergedAnnotation(returnType.getMethod(), ApiSuccessMessage.class);
    if (methodAnnotation != null) {
      return methodAnnotation.value();
    }

    ApiSuccessMessage typeAnnotation =
        AnnotatedElementUtils.findMergedAnnotation(
            returnType.getContainingClass(), ApiSuccessMessage.class);
    if (typeAnnotation != null) {
      return typeAnnotation.value();
    }

    return DEFAULT_SUCCESS_MESSAGE;
  }
}
