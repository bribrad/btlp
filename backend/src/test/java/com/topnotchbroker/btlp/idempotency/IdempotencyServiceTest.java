package com.topnotchbroker.btlp.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.topnotchbroker.btlp.idempotency.IdempotencyKeyRepository.StoredResponse;
import com.topnotchbroker.btlp.web.ConflictException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Docker-free unit test covering the idempotency reserve/replay/guard logic with an in-memory fake
 * repository (no Mockito, to stay independent of the JVM's mock-maker support).
 */
class IdempotencyServiceTest {

  private final FakeRepository repository = new FakeRepository();
  private final ObjectMapper objectMapper =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
          .build();
  private final IdempotencyService service = new IdempotencyService(repository, objectMapper);

  record Sample(String id, OffsetDateTime at) {}

  @Test
  void blankKeyRunsWorkWithoutTouchingStore() {
    AtomicInteger calls = new AtomicInteger();

    Sample result =
        service.run(
            "  ",
            "op",
            Sample.class,
            () -> {
              calls.incrementAndGet();
              return new Sample("a", null);
            });

    assertEquals("a", result.id());
    assertEquals(1, calls.get());
    assertTrue(repository.store.isEmpty());
  }

  @Test
  void firstCallReservesRunsAndStoresResponse() {
    Sample sample = new Sample("a", OffsetDateTime.parse("2026-01-01T00:00:00Z"));

    Sample result = service.run("k", "op", Sample.class, () -> sample);

    assertSame(sample, result);
    assertEquals("op", repository.store.get("k").operation());
    assertNotNull(repository.store.get("k").responseBody());
  }

  @Test
  void sameKeyAndOperationReplaysStoredResponseWithoutRunning() throws Exception {
    Sample stored = new Sample("a", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    repository.store.put("k", new StoredResponse("op", objectMapper.writeValueAsString(stored)));
    AtomicInteger calls = new AtomicInteger();

    Sample result =
        service.run(
            "k",
            "op",
            Sample.class,
            () -> {
              calls.incrementAndGet();
              return new Sample("SHOULD_NOT_RUN", null);
            });

    assertEquals(stored, result);
    assertEquals(0, calls.get());
  }

  @Test
  void keyReusedForDifferentOperationThrowsConflict() throws Exception {
    String json = objectMapper.writeValueAsString(new Sample("a", null));
    repository.store.put("k", new StoredResponse("other-op", json));

    assertThrows(
        ConflictException.class,
        () -> service.run("k", "op", Sample.class, () -> new Sample("x", null)));
  }

  /** In-memory stand-in for {@link IdempotencyKeyRepository}; the JDBC template is never used. */
  private static final class FakeRepository extends IdempotencyKeyRepository {
    private final Map<String, StoredResponse> store = new HashMap<>();

    private FakeRepository() {
      super(null);
    }

    @Override
    public boolean reserve(String key, String operation) {
      if (store.containsKey(key)) {
        return false;
      }
      store.put(key, new StoredResponse(operation, null));
      return true;
    }

    @Override
    public Optional<StoredResponse> find(String key) {
      return Optional.ofNullable(store.get(key));
    }

    @Override
    public void storeResponse(String key, String responseBody) {
      StoredResponse current = store.get(key);
      store.put(key, new StoredResponse(current.operation(), responseBody));
    }
  }
}
