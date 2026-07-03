package com.topnotchbroker.btlp.audit;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Data access for {@code audit_events} using explicit SQL. */
@Repository
public class AuditEventRepository {

  private static final String INSERT_SQL =
      """
      INSERT INTO audit_events (entity_type, entity_id, action, actor)
      VALUES (:entityType, :entityId, :action, :actor)
      RETURNING *
      """;

  private static final RowMapper<AuditEvent> ROW_MAPPER =
      (rs, rowNum) ->
          new AuditEvent(
              rs.getObject("id", UUID.class),
              AuditEntityType.valueOf(rs.getString("entity_type")),
              rs.getObject("entity_id", UUID.class),
              AuditAction.valueOf(rs.getString("action")),
              rs.getString("actor"),
              rs.getObject("occurred_at", OffsetDateTime.class));

  private final NamedParameterJdbcTemplate jdbc;

  public AuditEventRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public AuditEvent insert(
      AuditEntityType entityType, UUID entityId, AuditAction action, String actor) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("entityType", entityType.name(), Types.VARCHAR)
            .addValue("entityId", entityId, Types.OTHER)
            .addValue("action", action.name(), Types.VARCHAR)
            .addValue("actor", actor, Types.VARCHAR);
    return jdbc.queryForObject(INSERT_SQL, params, ROW_MAPPER);
  }

  public List<AuditEvent> findPage(
      AuditEntityType entityType, UUID entityId, int limit, int offset) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT * FROM audit_events");
    appendFilters(sql, params, entityType, entityId);
    sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset");
    params.addValue("limit", limit).addValue("offset", offset);
    return jdbc.query(sql.toString(), params, ROW_MAPPER);
  }

  public long count(AuditEntityType entityType, UUID entityId) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM audit_events");
    appendFilters(sql, params, entityType, entityId);
    Long total = jdbc.queryForObject(sql.toString(), params, Long.class);
    return total != null ? total : 0L;
  }

  private static void appendFilters(
      StringBuilder sql,
      MapSqlParameterSource params,
      AuditEntityType entityType,
      UUID entityId) {
    List<String> conditions = new ArrayList<>();
    if (entityType != null) {
      conditions.add("entity_type = :entityType");
      params.addValue("entityType", entityType.name(), Types.VARCHAR);
    }
    if (entityId != null) {
      conditions.add("entity_id = :entityId");
      params.addValue("entityId", entityId, Types.OTHER);
    }
    if (!conditions.isEmpty()) {
      sql.append(" WHERE ").append(String.join(" AND ", conditions));
    }
  }
}
