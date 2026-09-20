package com.topnotchbroker.btlp.dispatch;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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

  /** Actor recorded for transitions applied by the background expiry sweeper. */
  static final String SYSTEM_ACTOR = "system";

  private static final String INSERT_SQL =
      """
      INSERT INTO assignments (job_id, driver_id, state, expires_at, created_by, updated_by)
      VALUES (:jobId, :driverId, 'PENDING', :expiresAt, :actor, :actor)
      RETURNING *
      """;

  private static final String SELECT_BY_ID_SQL = "SELECT * FROM assignments WHERE id = :id";

  private static final String ACTIVE_STATES = "('PENDING', 'ACCEPTED')";

  private static final String SELECT_ACTIVE_BY_JOB_SQL =
      "SELECT * FROM assignments WHERE job_id = :jobId AND state IN " + ACTIVE_STATES;

  private static final String HAS_ACTIVE_SQL =
      "SELECT count(*) FROM assignments WHERE job_id = :jobId AND state IN " + ACTIVE_STATES;

  private static final String ACCEPT_SQL =
      """
      UPDATE assignments
      SET state = 'ACCEPTED', accepted_at = now(), updated_at = now(), updated_by = :actor
      WHERE id = :id AND state = 'PENDING'
      RETURNING *
      """;

  private static final String REJECT_SQL =
      """
      UPDATE assignments
      SET state = 'REJECTED', updated_at = now(), updated_by = :actor
      WHERE id = :id AND state = 'PENDING'
      RETURNING *
      """;

  private static final String COMPLETE_SQL =
      """
      UPDATE assignments
      SET state = 'COMPLETED', updated_at = now(), updated_by = :actor
      WHERE id = :id AND state = 'ACCEPTED'
      RETURNING *
      """;

  private static final String CANCEL_SQL =
      """
      UPDATE assignments
      SET state = 'CANCELED', updated_at = now(), updated_by = :actor
      WHERE id = :id AND state IN ('PENDING', 'ACCEPTED')
      RETURNING *
      """;

  private static final String EXPIRE_STALE_SQL =
      """
      UPDATE assignments
      SET state = 'EXPIRED', updated_at = now(), updated_by = :actor
      WHERE state = 'PENDING' AND expires_at <= now()
      RETURNING *
      """;

  private static final String ORDER_BY_NEWEST = " ORDER BY assigned_at DESC, id DESC";

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
              rs.getString("created_by"),
              rs.getString("updated_by"),
              rs.getObject("created_at", OffsetDateTime.class),
              rs.getObject("updated_at", OffsetDateTime.class));

  private final NamedParameterJdbcTemplate jdbc;

  public AssignmentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Assignment insert(UUID jobId, UUID driverId, OffsetDateTime expiresAt, String actor) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("jobId", jobId, Types.OTHER)
            .addValue("driverId", driverId, Types.OTHER)
            .addValue("expiresAt", expiresAt, Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("actor", actor, Types.VARCHAR);
    return jdbc.queryForObject(INSERT_SQL, params, ROW_MAPPER);
  }

  public Optional<Assignment> findById(UUID id) {
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id, Types.OTHER);
    return jdbc.query(SELECT_BY_ID_SQL, params, ROW_MAPPER).stream().findFirst();
  }

  /**
   * Returns the job's {@code PENDING} or {@code ACCEPTED} assignment, if any. At most one can exist
   * because {@link #hasActiveAssignment} gates every dispatch.
   */
  public Optional<Assignment> findActiveByJobId(UUID jobId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("jobId", jobId, Types.OTHER);
    return jdbc.query(SELECT_ACTIVE_BY_JOB_SQL, params, ROW_MAPPER).stream().findFirst();
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

  /**
   * Returns one page of assignments newest-first, optionally narrowed by job, driver, and state. A
   * null filter value means "no filter". Scoping to a job yields that job's dispatch history,
   * including the canceled assignments a reassignment left behind.
   */
  public List<Assignment> findPage(
      UUID jobId, UUID driverId, AssignmentState state, int limit, int offset) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT * FROM assignments");
    appendFilters(sql, params, jobId, driverId, state);
    sql.append(ORDER_BY_NEWEST).append(" LIMIT :limit OFFSET :offset");
    params.addValue("limit", limit).addValue("offset", offset);
    return jdbc.query(sql.toString(), params, ROW_MAPPER);
  }

  public long count(UUID jobId, UUID driverId, AssignmentState state) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM assignments");
    appendFilters(sql, params, jobId, driverId, state);
    Long total = jdbc.queryForObject(sql.toString(), params, Long.class);
    return total != null ? total : 0L;
  }

  /** Atomically transitions a {@code PENDING} assignment to {@code ACCEPTED}. */
  public Optional<Assignment> accept(UUID id, String actor) {
    return transition(ACCEPT_SQL, id, actor);
  }

  /** Atomically transitions a {@code PENDING} assignment to {@code REJECTED}. */
  public Optional<Assignment> reject(UUID id, String actor) {
    return transition(REJECT_SQL, id, actor);
  }

  /** Atomically transitions an {@code ACCEPTED} assignment to {@code COMPLETED}. */
  public Optional<Assignment> complete(UUID id, String actor) {
    return transition(COMPLETE_SQL, id, actor);
  }

  /** Atomically transitions a {@code PENDING} or {@code ACCEPTED} assignment to {@code CANCELED}. */
  public Optional<Assignment> cancel(UUID id, String actor) {
    return transition(CANCEL_SQL, id, actor);
  }

  /**
   * Transitions every {@code PENDING} assignment past its {@code expires_at} to {@code EXPIRED} in a
   * single statement, returning the expired rows so callers can cascade side effects.
   */
  public List<Assignment> expireStale() {
    return jdbc.query(
        EXPIRE_STALE_SQL,
        new MapSqlParameterSource().addValue("actor", SYSTEM_ACTOR, Types.VARCHAR),
        ROW_MAPPER);
  }

  /**
   * Runs a guarded state-transition update. The {@code WHERE} clause enforces the required current
   * state, so an empty result means the assignment was missing or not in the expected state.
   */
  private Optional<Assignment> transition(String sql, UUID id, String actor) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", id, Types.OTHER)
            .addValue("actor", actor, Types.VARCHAR);
    return jdbc.query(sql, params, ROW_MAPPER).stream().findFirst();
  }

  private static void appendFilters(
      StringBuilder sql,
      MapSqlParameterSource params,
      UUID jobId,
      UUID driverId,
      AssignmentState state) {
    List<String> conditions = new ArrayList<>();
    if (jobId != null) {
      conditions.add("job_id = :jobId");
      params.addValue("jobId", jobId, Types.OTHER);
    }
    if (driverId != null) {
      conditions.add("driver_id = :driverId");
      params.addValue("driverId", driverId, Types.OTHER);
    }
    if (state != null) {
      conditions.add("state = :state");
      params.addValue("state", state.name(), Types.VARCHAR);
    }
    if (!conditions.isEmpty()) {
      sql.append(" WHERE ").append(String.join(" AND ", conditions));
    }
  }
}
