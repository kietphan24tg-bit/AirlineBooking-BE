package com.example.moduleai.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("deployed-chat-e2e")
@EnabledIfEnvironmentVariable(named = "CHAT_DEPLOY_E2E_ENABLED", matches = "(?i)true")
class DeployedChatE2ETest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final List<UUID> created = new ArrayList<>();
  private HttpClient owner;
  private HttpClient stranger;
  private String baseUrl;
  private boolean httpApiOnly;

  @BeforeEach
  void setUp() {
    baseUrl = System.getenv("STAGING_BASE_URL");
    assertThat(baseUrl).as("STAGING_BASE_URL is required for opt-in acceptance").isNotBlank();
    baseUrl = baseUrl.replaceAll("/+$", "");
    assertThat(URI.create(baseUrl).getScheme()).isIn("http", "https");
    httpApiOnly = "true".equalsIgnoreCase(System.getenv("CHAT_DEPLOY_HTTP_API_ONLY"));
    if (httpApiOnly) {
      assertThat(URI.create(baseUrl).getScheme()).isEqualTo("http");
    }
    owner = client();
    stranger = client();
  }

  @AfterEach
  void cleanUp() throws Exception {
    List<UUID> failed = new ArrayList<>();
    for (UUID id : created) {
      if (send(owner, "DELETE", "/conversations/" + id, null).statusCode() != 204) {
        failed.add(id);
      }
    }
    assertThat(failed).as("Only test-created conversations should be purged").isEmpty();
  }

  @Test
  void guestRefreshIdempotencyOwnershipAndNewChat() throws Exception {
    HttpResponse<String> initial = send(owner, "GET", "/conversations/active", null);
    assertThat(initial.statusCode()).isEqualTo(204);
    List<String> cookies = initial.headers().allValues("set-cookie");
    assertThat(cookies).as("A fresh guest must receive identity cookie").isNotEmpty();
    if (baseUrl.startsWith("http://") && !httpApiOnly) {
      boolean requiresHttps =
          cookies.stream()
              .anyMatch(cookie -> cookie.toLowerCase(java.util.Locale.ROOT).contains("; secure"));
      assertThat(requiresHttps)
          .as("HTTP staging cannot retain a Secure guest cookie; use HTTPS")
          .isFalse();
    }

    UUID messageId = UUID.randomUUID();
    JsonNode first = chat("What documents do I need for a domestic flight?", messageId);
    UUID oldId = UUID.fromString(first.path("conversation").path("id").asText());
    created.add(oldId);
    assertThat(data(send(owner, "GET", "/conversations/active", null)).path("id").asText())
        .isEqualTo(oldId.toString());

    JsonNode replay = chat("What documents do I need for a domestic flight?", messageId);
    assertThat(replay.path("answer")).isEqualTo(first.path("answer"));
    assertThat(replay.path("conversation").path("id").asText()).isEqualTo(oldId.toString());
    assertThat(messages(oldId).path("items").size()).isEqualTo(2);

    assertThat(send(stranger, "GET", "/conversations/" + oldId + "/messages", null).statusCode())
        .isIn(403, 404);
    assertThat(send(stranger, "DELETE", "/conversations/" + oldId, null).statusCode())
        .isIn(403, 404);
    assertThat(messages(oldId).path("items").size()).isEqualTo(2);

    JsonNode archived = data(send(owner, "POST", "/conversations/active/archive", "{}"));
    assertThat(archived.path("id").asText()).isEqualTo(oldId.toString());
    assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");
    assertThat(send(owner, "GET", "/conversations/active", null).statusCode()).isEqualTo(204);
    assertThat(messages(oldId).path("items").size()).isEqualTo(2);

    JsonNode next =
        chat("What documents do I need for an international flight?", UUID.randomUUID());
    UUID newId = UUID.fromString(next.path("conversation").path("id").asText());
    if (!created.contains(newId)) {
      created.add(newId);
    }
    assertThat(newId).isNotEqualTo(oldId);
    assertThat(messages(oldId).path("items").size()).isEqualTo(2);
  }

  private HttpClient client() {
    CookieManager cookies =
        new CookieManager(null, CookiePolicy.ACCEPT_ALL) {
          @Override
          public void put(URI uri, Map<String, List<String>> headers) throws java.io.IOException {
            super.put(uri, headers);
            // API-only probe deliberately does not simulate browser Secure-cookie behavior.
            if (httpApiOnly) {
              for (HttpCookie cookie : getCookieStore().getCookies()) {
                cookie.setSecure(false);
              }
            }
          }
        };
    return HttpClient.newBuilder()
        .cookieHandler(cookies)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  private JsonNode chat(String message, UUID id) throws Exception {
    return data(
        send(
            owner,
            "POST",
            "/messages",
            mapper.writeValueAsString(
                Map.of("message", message, "clientMessageId", id.toString()))));
  }

  private JsonNode messages(UUID id) throws Exception {
    return data(send(owner, "GET", "/conversations/" + id + "/messages", null));
  }

  private JsonNode data(HttpResponse<String> response) throws Exception {
    assertThat(response.statusCode()).as("Expected successful chat API response").isEqualTo(200);
    JsonNode body = mapper.readTree(response.body());
    return body.has("data") ? body.path("data") : body;
  }

  private HttpResponse<String> send(HttpClient client, String method, String path, String body)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + "/api/chatbot" + path))
            .timeout(Duration.ofSeconds(90))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
