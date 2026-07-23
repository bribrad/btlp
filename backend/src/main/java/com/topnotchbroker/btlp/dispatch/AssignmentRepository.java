package com.topnotchbroker.btlp.dispatch;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Data access for {@code assignments} using explicit SQL, mirroring the other repositories. */
@Repository
public class AssignmentRepository {

  private static final String INSERT_SQL =
      """
      INSERT INTO assignments (job_id, driver_id, state, expires_at)
      VALUES (:jobId, :driverId, 'PENDING', :expiresAt)
      RETURNING *
      """;

  private static final String SELECT_BY_ID_SQL = "SELECT * FROM assignments WHERE id = :id";

  private static final String HAS_ACTIVE_SQL =
      """
      SELECT count(*) FROM assignments
      WHERE job_id = :jobId AND state IN ('PENDING', 'ACCEPTED')
      """;

  private static final String ACCEPT_SQL =
      """
      UPDATE assignments
      SET state = 'ACCEPTED', accepted_at = now(), updated_at = now()
      WHERE id = :id AND state = 'PENDING'
      RETURNING *
      """;

  private static final String REJECT_SQL =
      """
      UPDATE assignments
      SET state = 'REJECTED', updated_at = now()
      WHERE id = :id AND state = 'PENDING'
      RETURNING *
      """;

  private static final String COMPLETE_SQL =
      """
      UPDATE assignments
      SET state = 'COMPLETED', updated_at = now()
      WHERE id = :id AND state = 'ACCEPTED'
      RETURNING *
      """;

  private static final String EXPIRE_STALE_SQL =
      """
      UPDATE assignments
      SET state = 'EXPIRED', updated_at = now()
      WHERE state = 'PENDING' AND expires_at <= now()
      RETURNING *
      """;

  private static final RowMapper<Assignment> ROW_MAPPER =
      (rs, rowNum) ->
          new Assignment(
              rs.getObject("id", UUID.class),
              rs.getObject("job_id", UUID.class),
              rs.getObject("driver_id", UUID.class),
              AssignmentState.valueOf(rs.getString("state")),
              rs.getObject("assigned_at", OffsetDateTime.class),
              rs.getObject("accepted_at", OffsetDateTime.class),
              rs.getObject("expires_at", OffsetDateTime.class),
              rs.getObject("created_at", OffsetDateTime.class),
              rs.getObject("updated_at", OffsetDateTime.class));

  private final NamedParameterJdbcTemplate jdbc;

  public AssignmentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Assignment insert(UUID jobId, UUID driverId, OffsetDateTime expiresAt) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("jobId", jobId, Types.OTHER)
            .addValue("driverId", driverId, Types.OTHER)
            .addValue("expiresAt", expiresAt, Types.TIMESTAMP_WITH_TIMEZONE);
    return jdbc.queryForObject(INSERT_SQL, params, ROW_MAPPER);
  }

  public Optional<Assignment> findById(UUID id) {
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id, Types.OTHER);
    return jdbc.query(SELECT_BY_ID_SQL, params, ROW_MAPPER).stream().findFirst();
  }

  /**
   * Returns {@code true} when there is already a {@code PENDING} or {@code ACCEPTED} assignment
   * for the given job, preventing a second dispatch to the same job.
   */
  public boolean hasActiveAssignment(UUID jobId) {
    Long count =
        jdbc.queryForObject(
            HAS_ACTIVE_SQL,
            new MapSqlParameterSource().addValue("jobId", jobId, Types.OTHER),
            Long.class);
    return count != null && count > 0;
  }

  /** Atomically transitions a {@code PENDING} assignment to {@code ACCEPTED}. */
  public Optional<Assignment> accept(UUID id) {
    return transition(ACCEPT_SQL, id);
  }

  /** Atomically transitions a {@code PENDING} assignment to {@code REJECTED}. */
  public Optional<Assignment> reject(UUID id) {
    return transition(REJECT_SQL, id);
  }

  /** Atomically transitions an {@code ACCEPTED} assignment to {@code COMPLETED}. */
  public Optional<Assignment> complete(UUID id) {
    return transition(COMPLETE_SQL, id);
  }

  /**
   * Transitions every {@code PENDING} assignment past its {@code expires_at} to {@code EXPIRED} in a
   * single statement, returning the expired rows so callers can cascade side effects.
   */
  public List<Assignment> expireStale() {
    return jdbc.query(EXPIRE_STALE_SQL, new MapSqlParameterSource(), ROW_MAPPER);
  }

  /**
   * Runs a guarded state-transition update. The {@code WHERE} clause enforces the required current
   * state, so an empty result means the assignment was missing or not in the expected state.
   */
  private Optional<Assignment> transition(String sql, UUID id) {
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id, Types.OTHER);
    return jdbc.query(sql, params, ROW_MAPPER).stream().findFirst();
  }
}
