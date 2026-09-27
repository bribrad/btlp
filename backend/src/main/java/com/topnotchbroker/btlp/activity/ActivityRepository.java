package com.topnotchbroker.btlp.activity;

import com.topnotchbroker.btlp.audit.AuditAction;
import com.topnotchbroker.btlp.audit.AuditEntityType;
import com.topnotchbroker.btlp.job.JobType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only data access for the operations timeline. Audit events store only {@code entity_type} +
 * {@code entity_id}, so this join walks each event back to the job and load it belongs to —
 * assignment &rarr; job &rarr; load — letting a dispatcher scope the timeline to a load without the
 * client first fetching that load's jobs and assignments.
 *
 * <p>Every join is on a primary key, so an event can never fan out into several rows.
 */
@Repository
public class ActivityRepository {

  private static final String FROM_SQL =
      """
      FROM audit_events e
      LEFT JOIN assignments a ON e.entity_type = 'ASSIGNMENT' AND a.id = e.entity_id
      LEFT JOIN drivers d ON d.id = a.driver_id
      LEFT JOIN jobs j
             ON j.id = COALESCE(a.job_id, CASE WHEN e.entity_type = 'JOB' THEN e.entity_id END)
      LEFT JOIN loads l
             ON l.id = COALESCE(j.load_id, CASE WHEN e.entity_type = 'LOAD' THEN e.entity_id END)
      """;

  private static final String SELECT_SQL =
      """
      SELECT e.id, e.seq, e.occurred_at, e.actor, e.entity_type, e.entity_id, e.action, e.detail,
             l.id AS load_id, l.origin, l.destination,
             j.id AS job_id, j.job_type, j.sequence AS job_sequence,
             a.id AS assignment_id, d.id AS driver_id, d.name AS driver_name
      """
          + FROM_SQL;

  /** Insertion order, newest first — stable across refreshes even for same-transaction events. */
  private static final String ORDER_BY_SQL = " ORDER BY e.seq DESC";

  private static final RowMapper<ActivityEvent> ROW_MAPPER =
      (rs, rowNum) ->
          new ActivityEvent(
              rs.getObject("id", UUID.class),
              rs.getLong("seq"),
              rs.getObject("occurred_at", OffsetDateTime.class),
              rs.getString("actor"),
              AuditEntityType.valueOf(rs.getString("entity_type")),
              rs.getObject("entity_id", UUID.class),
              AuditAction.valueOf(rs.getString("action")),
              rs.getString("detail"),
              rs.getObject("load_id", UUID.class),
              rs.getString("origin"),
              rs.getString("destination"),
              rs.getObject("job_id", UUID.class),
              jobType(rs),
              nullableInt(rs, "job_sequence"),
              rs.getObject("assignment_id", UUID.class),
              rs.getObject("driver_id", UUID.class),
              rs.getString("driver_name"));

  private final NamedParameterJdbcTemplate jdbc;

  public ActivityRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ActivityEvent> findPage(UUID loadId, UUID jobId, int limit, int offset) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder(SELECT_SQL);
    appendFilters(sql, params, loadId, jobId);
    sql.append(ORDER_BY_SQL).append(" LIMIT :limit OFFSET :offset");
    params.addValue("limit", limit).addValue("offset", offset);
    return jdbc.query(sql.toString(), params, ROW_MAPPER);
  }

  public long count(UUID loadId, UUID jobId) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT count(*) ").append(FROM_SQL);
    appendFilters(sql, params, loadId, jobId);
    Long total = jdbc.queryForObject(sql.toString(), params, Long.class);
    return total != null ? total : 0L;
  }

  private static void appendFilters(
      StringBuilder sql, MapSqlParameterSource params, UUID loadId, UUID jobId) {
    List<String> conditions = new ArrayList<>();
    if (loadId != null) {
      conditions.add("l.id = :loadId");
      params.addValue("loadId", loadId, Types.OTHER);
    }
    if (jobId != null) {
      conditions.add("j.id = :jobId");
      params.addValue("jobId", jobId, Types.OTHER);
    }
    if (!conditions.isEmpty()) {
      sql.append(" WHERE ").append(String.join(" AND ", conditions));
    }
  }

  private static JobType jobType(ResultSet rs) throws SQLException {
    String value = rs.getString("job_type");
    return value != null ? JobType.valueOf(value) : null;
  }

  /** {@code getInt} maps SQL NULL to 0, which would read as leg 0 on a load-level event. */
  private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }
}
