package com.example.moduleai.tool.catalog;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.ToolPolicy;
import com.example.moduleai.tool.core.ToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

public record ToolDescriptor(
    String name,
    String description,
    ToolPolicy policy,
    Class<?> inputType,
    String inputSchema,
    Object bean,
    Method method,
    ToolCallback callback,
    boolean exposedToLlm) {

  @SuppressWarnings("unchecked")
  public ToolResult<?> invoke(
      ObjectMapper objectMapper, String canonicalArguments, AgentToolContext context) {
    try {
      Object input = objectMapper.readValue(canonicalArguments, inputType);
      Object result = method.invoke(bean, input, new ToolContext(context.toToolContextMap()));
      return (ToolResult<?>) result;
    } catch (InvocationTargetException exception) {
      Throwable target = exception.getTargetException();
      if (target instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Tool invocation failed: " + name, target);
    } catch (JsonProcessingException | IllegalAccessException exception) {
      throw new IllegalStateException("Tool invocation failed: " + name, exception);
    }
  }
}
