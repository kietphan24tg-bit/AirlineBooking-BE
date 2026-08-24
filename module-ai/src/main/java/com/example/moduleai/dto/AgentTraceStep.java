package com.example.moduleai.dto;

/**
 * Represents a single step in the Agent execution trace. Used for debugging and transparency —
 * shows what the Agent did internally.
 *
 * @param type Step type: "routing", "guardrail", "tool_call", "fallback"
 * @param label Human-readable description of the step
 * @param status Step result: "ok", "blocked", "skipped", "error"
 * @param tool Tool name if type is "tool_call", null otherwise
 */
public record AgentTraceStep(String type, String label, String status, String tool) {}
