package com.topnotchbroker.btlp.idempotency;

import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Data access for {@code idempotency_keys} using explicit SQL. */
@Repository
public class IdempotencyKeyRepository {

  /**
   * Reserves the key if unseen. {@code ON CONFLICT DO NOTHING} makes this atomic: a row is returned
   * only when this caller won the race, so a concurrent duplicate blocks until the winner commits
   * and then reads back the stored response.
   */
  private static final String RESERVE_SQL =
      """
      INSERT INTO idempotency_keys (idempotency_key, operation)
      VALUES (:key, :operation)
      ON CONFLICT (idempotency_key) DO NOTHING
      RETURNING id
      """;

  private static final String FIND_SQL =
      "SELECT operation, response_body FROM idempotency_keys WHERE idempotency_key = :key";

  private static final String STORE_RESPONSE_SQL =
      "UPDATE idempotency_keys SET response_body = CAST(:responseBody AS jsonb) WHERE idempotency_key = :key";

  private final NamedParameterJdbcTemplate jdbc;

  public IdempotencyKeyRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Attempts to reserve {@code key}; returns {@code true} when this caller created the row. */
  public boolean reserve(String key, String operation) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("key", key, Types.VARCHAR)
            .addValue("operation", operation, Types.VARCHAR);
    return !jdbc.query(RESERVE_SQL, params, (rs, rowNum) -> rs.getObject("id", UUID.class)).isEmpty();
  }

  public Optional<StoredResponse> find(String key) {
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("key", key, Types.VARCHAR);
    return jdbc
        .query(
            FIND_SQL,
            params,
            (rs, rowNum) -> new StoredResponse(rs.getString("operation"), rs.getString("response_body")))
        .stream()
        .findFirst();
  }

  public void storeResponse(String key, String responseBody) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("key", key, Types.VARCHAR)
            .addValue("responseBody", responseBody, Types.VARCHAR);
    jdbc.update(STORE_RESPONSE_SQL, params);
  }

  /** A previously recorded idempotent outcome. {@code responseBody} is null until work completes. */
  public record StoredResponse(String operation, String responseBody) {}
}
