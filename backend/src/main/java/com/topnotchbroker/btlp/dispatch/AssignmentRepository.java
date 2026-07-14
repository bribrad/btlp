package com.topnotchbroker.btlp.dispatch;

import java.sql.Types;
import java.time.OffsetDateTime;
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
      INSERT INTO assignments (job_id, driver_id, state)
      VALUES (:jobId, :driverId, 'PENDING')
      RETURNING *
      """;

  private static final String SELECT_BY_ID_SQL = "SELECT * FROM assignments WHERE id = :id";

  private static final String HAS_ACTIVE_SQL =
      """
      SELECT count(*) FROM assignments
      WHERE job_id = :jobId AND state IN ('PENDING', 'ACCEPTED')
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
              rs.getObject("created_at", OffsetDateTime.class),
              rs.getObject("updated_at", OffsetDateTime.class));

  private final NamedParameterJdbcTemplate jdbc;

  public AssignmentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Assignment insert(UUID jobId, UUID driverId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("jobId", jobId, Types.OTHER)
            .addValue("driverId", driverId, Types.OTHER);
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
}
