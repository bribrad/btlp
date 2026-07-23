package com.topnotchbroker.btlp.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.topnotchbroker.btlp.idempotency.IdempotencyKeyRepository.StoredResponse;
import com.topnotchbroker.btlp.web.ConflictException;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a unit of work at most once per client-supplied {@code Idempotency-Key}, replaying the
 * originally stored response shape on retries.
 *
 * <p>When a key is provided, the reservation, the work, and the response write all happen in the
 * caller's transaction, so a failed operation rolls back its reservation and can be retried. A
 * genuine retry (same key + operation) returns the stored response without re-executing, which is
 * what prevents duplicate assignment state changes; reusing a key for a different operation is
 * rejected.
 */
@Service
public class IdempotencyService {

  private final IdempotencyKeyRepository repository;
  private final ObjectMapper objectMapper;

  public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  /**
   * Executes {@code work} idempotently. With a blank key the work simply runs. Otherwise the first
   * call for {@code key} executes and records the response; later calls replay it.
   *
   * @param key the client-supplied idempotency key (may be null/blank to opt out)
   * @param operation stable identifier of the logical operation, used to detect key reuse
   * @param type the response type, for (de)serialization
   * @param work the operation to run at most once
   */
  @Transactional
  public <T> T run(String key, String operation, Class<T> type, Supplier<T> work) {
    if (key == null || key.isBlank()) {
      return work.get();
    }
    Optional<StoredResponse> existing = repository.find(key);
    if (existing.isPresent()) {
      return replay(existing.get(), key, operation, type);
    }
    if (!repository.reserve(key, operation)) {
      // Lost a race to a concurrent request holding the same key; read back its outcome.
      StoredResponse stored =
          repository
              .find(key)
              .orElseThrow(
                  () ->
                      new ConflictException(
                          "A request with Idempotency-Key '" + key + "' is still in progress."));
      return replay(stored, key, operation, type);
    }
    T result = work.get();
    repository.storeResponse(key, serialize(result));
    return result;
  }

  private <T> T replay(StoredResponse stored, String key, String operation, Class<T> type) {
    if (!stored.operation().equals(operation)) {
      throw new ConflictException(
          "Idempotency-Key '" + key + "' was already used for a different operation.");
    }
    if (stored.responseBody() == null) {
      throw new ConflictException(
          "A request with Idempotency-Key '" + key + "' is still in progress.");
    }
    return deserialize(stored.responseBody(), type);
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize idempotent response", e);
    }
  }

  private <T> T deserialize(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize stored idempotent response", e);
    }
  }
}
