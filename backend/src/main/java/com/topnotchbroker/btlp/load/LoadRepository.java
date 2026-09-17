package com.topnotchbroker.btlp.load;

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

/**
 * Data access for {@code loads} using explicit SQL for full control over queries. Insert and update
 * use {@code RETURNING *} to return the DB-managed columns (id, timestamps) in a single round trip.
 */
@Repository
public class LoadRepository {

  private static final String INSERT_SQL =
      """
      INSERT INTO loads (
          customer_id, origin, destination,
          pickup_window_start, pickup_window_end,
          dropoff_window_start, dropoff_window_end,
          rate_amount, rate_currency, notes, status,
          created_by, updated_by
      ) VALUES (
          :customerId, :origin, :destination,
          :pickupWindowStart, :pickupWindowEnd,
          :dropoffWindowStart, :dropoffWindowEnd,
          :rateAmount, :rateCurrency, :notes, :status,
          :createdBy, :updatedBy
      )
      RETURNING *
      """;

  private static final String UPDATE_SQL =
      """
      UPDATE loads SET
          origin = :origin,
          destination = :destination,
          pickup_window_start = :pickupWindowStart,
          pickup_window_end = :pickupWindowEnd,
          dropoff_window_start = :dropoffWindowStart,
          dropoff_window_end = :dropoffWindowEnd,
          rate_amount = :rateAmount,
          rate_currency = :rateCurrency,
          notes = :notes,
          updated_by = :updatedBy,
          updated_at = now()
      WHERE id = :id
      RETURNING *
      """;

  private static final String SELECT_BY_ID_SQL = "SELECT * FROM loads WHERE id = :id";

  private static final String SEARCH_CONDITION =
      "(origin ILIKE :search OR destination ILIKE :search OR customer_id ILIKE :search)";

  private static final RowMapper<Load> ROW_MAPPER =
      (rs, rowNum) ->
          new Load(
              rs.getObject("id", UUID.class),
              rs.getString("customer_id"),
              rs.getString("origin"),
              rs.getString("destination"),
              rs.getObject("pickup_window_start", OffsetDateTime.class),
              rs.getObject("pickup_window_end", OffsetDateTime.class),
              rs.getObject("dropoff_window_start", OffsetDateTime.class),
              rs.getObject("dropoff_window_end", OffsetDateTime.class),
              rs.getBigDecimal("rate_amount"),
              stripToNull(rs.getString("rate_currency")),
              rs.getString("notes"),
              LoadStatus.valueOf(rs.getString("status")),
              rs.getString("created_by"),
              rs.getString("updated_by"),
              rs.getObject("created_at", OffsetDateTime.class),
              rs.getObject("updated_at", OffsetDateTime.class));

  private final NamedParameterJdbcTemplate jdbc;

  public LoadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Load insert(Load load) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("customerId", load.customerId(), Types.VARCHAR)
            .addValue("origin", load.origin(), Types.VARCHAR)
            .addValue("destination", load.destination(), Types.VARCHAR)
            .addValue("pickupWindowStart", load.pickupWindowStart(), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("pickupWindowEnd", load.pickupWindowEnd(), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("dropoffWindowStart", load.dropoffWindowStart(), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("dropoffWindowEnd", load.dropoffWindowEnd(), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("rateAmount", load.rateAmount(), Types.NUMERIC)
            .addValue("rateCurrency", load.rateCurrency(), Types.CHAR)
            .addValue("notes", load.notes(), Types.VARCHAR)
            .addValue("status", load.status().name(), Types.VARCHAR)
            .addValue("createdBy", load.createdBy(), Types.VARCHAR)
            .addValue("updatedBy", load.updatedBy(), Types.VARCHAR);
    return jdbc.queryForObject(INSERT_SQL, params, ROW_MAPPER);
  }

  public Optional<Load> update(UUID id, Load values) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", id, Types.OTHER)
            .addValue("origin", values.origin(), Types.VARCHAR)
            .addValue("destination", values.destination(), Types.VARCHAR)
            .addValue("pickupWindowStart", values.pickupWindowStart(), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("pickupWindowEnd", values.pickupWindowEnd(), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue(
                "dropoffWindowStart", values.dropoffWindowStart(), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("dropoffWindowEnd", values.dropoffWindowEnd(), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("rateAmount", values.rateAmount(), Types.NUMERIC)
            .addValue("rateCurrency", values.rateCurrency(), Types.CHAR)
            .addValue("notes", values.notes(), Types.VARCHAR)
            .addValue("updatedBy", values.updatedBy(), Types.VARCHAR);
    return jdbc.query(UPDATE_SQL, params, ROW_MAPPER).stream().findFirst();
  }

  public Optional<Load> findById(UUID id) {
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id, Types.OTHER);
    return jdbc.query(SELECT_BY_ID_SQL, params, ROW_MAPPER).stream().findFirst();
  }

  /**
   * Returns one page of loads, newest first, optionally narrowed by a free-text search over
   * origin/destination/customer and by status. A null filter value means "no filter".
   */
  public List<Load> findPage(String search, LoadStatus status, int limit, int offset) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT * FROM loads");
    appendFilters(sql, params, search, status);
    sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset");
    params.addValue("limit", limit).addValue("offset", offset);
    return jdbc.query(sql.toString(), params, ROW_MAPPER);
  }

  public long count(String search, LoadStatus status) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM loads");
    appendFilters(sql, params, search, status);
    Long total = jdbc.queryForObject(sql.toString(), params, Long.class);
    return total != null ? total : 0L;
  }

  public boolean existsById(UUID id) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM loads WHERE id = :id)",
            new MapSqlParameterSource().addValue("id", id, Types.OTHER),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  private static void appendFilters(
      StringBuilder sql, MapSqlParameterSource params, String search, LoadStatus status) {
    List<String> conditions = new ArrayList<>();
    if (search != null) {
      conditions.add(SEARCH_CONDITION);
      params.addValue("search", likePattern(search), Types.VARCHAR);
    }
    if (status != null) {
      conditions.add("status = :status");
      params.addValue("status", status.name(), Types.VARCHAR);
    }
    if (!conditions.isEmpty()) {
      sql.append(" WHERE ").append(String.join(" AND ", conditions));
    }
  }

  /** Wraps a search term for a contains-match, escaping the LIKE wildcards it may contain. */
  private static String likePattern(String search) {
    String escaped =
        search.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  private static String stripToNull(String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }
}
