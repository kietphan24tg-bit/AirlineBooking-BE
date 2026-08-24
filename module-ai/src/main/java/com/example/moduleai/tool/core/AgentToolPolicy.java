package com.example.moduleai.tool.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentToolPolicy {
  String tool();

  ToolOperation operation();

  ToolAccess access();

  boolean confirmationRequired() default false;

  boolean idempotencyRequired() default false;
}
