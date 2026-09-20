package com.topnotchbroker.btlp.dispatch;

import com.topnotchbroker.btlp.job.JobStatus;
import com.topnotchbroker.btlp.job.JobType;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only data access for the dispatch board. The board is a single join over jobs, loads,
 * assignments, and drivers so a dispatcher sees the queue in one round trip instead of the client
 * stitching three paginated lists together.
 *
 * <p>The join to {@code assignments} is restricted to the active states, and a job can hold at most
 * one of those, so the join cannot fan a job out into several rows.
 */
@Repository
public class DispatchBoardRepository {

  private static final String FROM_SQL =
      """
      FROM jobs j
      JOIN loads l ON l.id = j.load_id
      LEFT JOIN assignments a ON a.job_id = j.id AND a.state IN ('PENDING', 'ACCEPTED')
      LEFT JOIN drivers d ON d.id = a.driver_id
      WHERE j.status NOT IN ('COMPLETED', 'CANCELED')
      """;

  private static final String SELECT_SQL =
      """
      SELECT j.id AS job_id, j.load_id, j.job_type, j.sequence, j.status AS job_status,
             j.scheduled_at, l.origin, l.destination,
             a.id AS assignment_id, a.state AS assignment_state, a.assigned_at, a.expires_at,
             a.created_by AS assigned_by,
             d.id AS driver_id, d.name AS driver_name, d.phone AS driver_phone
      """
          + FROM_SQL;

  /**
   * Jobs still needing a driver sort first, then those awaiting an answer, then trips under way;
   * within a lane the soonest scheduled job leads and unscheduled ones fall to the end.
   */
  private static final String ORDER_BY_SQL =
      """
      ORDER BY CASE
                 WHEN a.id IS NULL THEN 0
                 WHEN a.state = 'PENDING' THEN 1
                 ELSE 2
               END,
               j.scheduled_at ASC NULLS LAST, j.created_at ASC, j.id ASC
      """;

  private static final String NEEDS_DISPATCH_FILTER = " AND a.id IS NULL";

  private static final String ASSIGNMENT_STATE_FILTER = " AND a.state = :assignmentState";

  private static final RowMapper<DispatchBoardEntry> ROW_MAPPER =
      (rs, rowNum) -> {
        String state = rs.getString("assignment_state");
        AssignmentState assignmentState = state != null ? AssignmentState.valueOf(state) : null;
        return new DispatchBoardEntry(
            rs.getObject("job_id", UUID.class),
            rs.getObject("load_id", UUID.class),
            JobType.valueOf(rs.getString("job_type")),
            rs.getInt("sequence"),
            JobStatus.valueOf(rs.getString("job_status")),
            rs.getObject("scheduled_at", OffsetDateTime.class),
            rs.getString("origin"),
            rs.getString("destination"),
            DispatchBoardLane.from(assignmentState),
            rs.getObject("assignment_id", UUID.class),
            assignmentState,
            rs.getObject("assigned_at", OffsetDateTime.class),
            rs.getObject("expires_at", OffsetDateTime.class),
            rs.getString("assigned_by"),
            rs.getObject("driver_id", UUID.class),
            rs.getString("driver_name"),
            rs.getString("driver_phone"));
      };

  private final NamedParameterJdbcTemplate jdbc;

  public DispatchBoardRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Returns one page of board entries, optionally narrowed to a single lane. */
  public List<DispatchBoardEntry> findPage(DispatchBoardLane lane, int limit, int offset) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder(SELECT_SQL);
    appendLaneFilter(sql, params, lane);
    sql.append(' ').append(ORDER_BY_SQL).append(" LIMIT :limit OFFSET :offset");
    params.addValue("limit", limit).addValue("offset", offset);
    return jdbc.query(sql.toString(), params, ROW_MAPPER);
  }

  public long count(DispatchBoardLane lane) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT count(*) ").append(FROM_SQL);
    appendLaneFilter(sql, params, lane);
    Long total = jdbc.queryForObject(sql.toString(), params, Long.class);
    return total != null ? total : 0L;
  }

  private static void appendLaneFilter(
      StringBuilder sql, MapSqlParameterSource params, DispatchBoardLane lane) {
    if (lane == null) {
      return;
    }
    if (lane == DispatchBoardLane.NEEDS_DISPATCH) {
      sql.append(NEEDS_DISPATCH_FILTER);
      return;
    }
    AssignmentState state =
        lane == DispatchBoardLane.PENDING_ACCEPTANCE
            ? AssignmentState.PENDING
            : AssignmentState.ACCEPTED;
    sql.append(ASSIGNMENT_STATE_FILTER);
    params.addValue("assignmentState", state.name(), Types.VARCHAR);
  }
}
