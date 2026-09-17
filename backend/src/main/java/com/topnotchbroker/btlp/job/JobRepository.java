package com.topnotchbroker.btlp.job;

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

/** Data access for {@code jobs} using explicit SQL, mirroring {@code LoadRepository}. */
@Repository
public class JobRepository {

  private static final String INSERT_SQL =
      """
      INSERT INTO jobs (load_id, job_type, sequence, status, scheduled_at)
      VALUES (:loadId, :jobType, :sequence, :status, :scheduledAt)
      RETURNING *
      """;

  private static final String UPDATE_SQL =
      """
      UPDATE jobs SET
          job_type = :jobType,
          sequence = :sequence,
          scheduled_at = :scheduledAt,
          updated_at = now()
      WHERE id = :id
      RETURNING *
      """;

  private static final String UPDATE_STATUS_SQL =
      "UPDATE jobs SET status = :status, updated_at = now() WHERE id = :id RETURNING *";

  private static final String SELECT_BY_ID_SQL = "SELECT * FROM jobs WHERE id = :id";

  /** Jobs of a single load read as a route, so they are ordered by leg instead of by age. */
  private static final String ORDER_BY_SEQUENCE = " ORDER BY sequence ASC";

  private static final String ORDER_BY_NEWEST = " ORDER BY created_at DESC, id DESC";

  private static final String NEXT_SEQUENCE_SQL =
      "SELECT COALESCE(MAX(sequence), 0) + 1 FROM jobs WHERE load_id = :loadId";

  private static final RowMapper<Job> ROW_MAPPER =
      (rs, rowNum) ->
          new Job(
              rs.getObject("id", UUID.class),
              rs.getObject("load_id", UUID.class),
              JobType.valueOf(rs.getString("job_type")),
              rs.getInt("sequence"),
              JobStatus.valueOf(rs.getString("status")),
              rs.getObject("scheduled_at", OffsetDateTime.class),
              rs.getObject("created_at", OffsetDateTime.class),
              rs.getObject("updated_at", OffsetDateTime.class));

  private final NamedParameterJdbcTemplate jdbc;

  public JobRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Job insert(Job job) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("loadId", job.loadId(), Types.OTHER)
            .addValue("jobType", job.jobType().name(), Types.VARCHAR)
            .addValue("sequence", job.sequence(), Types.INTEGER)
            .addValue("status", job.status().name(), Types.VARCHAR)
            .addValue("scheduledAt", job.scheduledAt(), Types.TIMESTAMP_WITH_TIMEZONE);
    return jdbc.queryForObject(INSERT_SQL, params, ROW_MAPPER);
  }

  public Optional<Job> update(UUID id, Job values) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", id, Types.OTHER)
            .addValue("jobType", values.jobType().name(), Types.VARCHAR)
            .addValue("sequence", values.sequence(), Types.INTEGER)
            .addValue("scheduledAt", values.scheduledAt(), Types.TIMESTAMP_WITH_TIMEZONE);
    return jdbc.query(UPDATE_SQL, params, ROW_MAPPER).stream().findFirst();
  }

  public Optional<Job> updateStatus(UUID id, JobStatus status) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", id, Types.OTHER)
            .addValue("status", status.name(), Types.VARCHAR);
    return jdbc.query(UPDATE_STATUS_SQL, params, ROW_MAPPER).stream().findFirst();
  }

  public Optional<Job> findById(UUID id) {
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id, Types.OTHER);
    return jdbc.query(SELECT_BY_ID_SQL, params, ROW_MAPPER).stream().findFirst();
  }

  /**
   * Returns one page of jobs, optionally narrowed by parent load, status, and type. A null filter
   * value means "no filter". Jobs scoped to a load are ordered by sequence; otherwise newest first.
   */
  public List<Job> findPage(
      UUID loadId, JobStatus status, JobType jobType, int limit, int offset) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT * FROM jobs");
    appendFilters(sql, params, loadId, status, jobType);
    sql.append(loadId != null ? ORDER_BY_SEQUENCE : ORDER_BY_NEWEST);
    sql.append(" LIMIT :limit OFFSET :offset");
    params.addValue("limit", limit).addValue("offset", offset);
    return jdbc.query(sql.toString(), params, ROW_MAPPER);
  }

  public long count(UUID loadId, JobStatus status, JobType jobType) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM jobs");
    appendFilters(sql, params, loadId, status, jobType);
    Long total = jdbc.queryForObject(sql.toString(), params, Long.class);
    return total != null ? total : 0L;
  }

  public boolean existsById(UUID id) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM jobs WHERE id = :id)",
            new MapSqlParameterSource().addValue("id", id, Types.OTHER),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  public int nextSequence(UUID loadId) {
    Integer next =
        jdbc.queryForObject(
            NEXT_SEQUENCE_SQL,
            new MapSqlParameterSource().addValue("loadId", loadId, Types.OTHER),
            Integer.class);
    return next != null ? next : 1;
  }

  private static void appendFilters(
      StringBuilder sql,
      MapSqlParameterSource params,
      UUID loadId,
      JobStatus status,
      JobType jobType) {
    List<String> conditions = new ArrayList<>();
    if (loadId != null) {
      conditions.add("load_id = :loadId");
      params.addValue("loadId", loadId, Types.OTHER);
    }
    if (status != null) {
      conditions.add("status = :status");
      params.addValue("status", status.name(), Types.VARCHAR);
    }
    if (jobType != null) {
      conditions.add("job_type = :jobType");
      params.addValue("jobType", jobType.name(), Types.VARCHAR);
    }
    if (!conditions.isEmpty()) {
      sql.append(" WHERE ").append(String.join(" AND ", conditions));
    }
  }
}
