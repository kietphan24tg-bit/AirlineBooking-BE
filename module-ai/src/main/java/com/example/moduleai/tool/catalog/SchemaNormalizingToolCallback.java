package com.example.moduleai.tool.catalog;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

final class SchemaNormalizingToolCallback implements ToolCallback {

  private final ToolCallback delegate;
  private final ToolDefinition definition;

  private SchemaNormalizingToolCallback(ToolCallback delegate, ToolDefinition definition) {
    this.delegate = delegate;
    this.definition = definition;
  }

  static ToolCallback from(ToolCallback delegate) {
    ToolDefinition original = delegate.getToolDefinition();
    String normalizedSchema = ToolSchemaNormalizer.normalize(original.inputSchema());
    if (normalizedSchema.equals(original.inputSchema())) {
      return delegate;
    }
    ToolDefinition normalizedDefinition =
        ToolDefinition.builder()
            .name(original.name())
            .description(original.description())
            .inputSchema(normalizedSchema)
            .build();
    return new SchemaNormalizingToolCallback(delegate, normalizedDefinition);
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return definition;
  }

  @Override
  public ToolMetadata getToolMetadata() {
    return delegate.getToolMetadata();
  }

  @Override
  public String call(String toolInput) {
    return delegate.call(toolInput);
  }

  @Override
  public String call(String toolInput, ToolContext toolContext) {
    return delegate.call(toolInput, toolContext);
  }
}
