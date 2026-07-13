package com.topnotchbroker.btlp.driver;

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

/** Data access for {@code drivers} using explicit SQL. */
@Repository
public class DriverRepository {

  private static final String INSERT_SQL =
      """
      INSERT INTO drivers (name, phone, license_number, status)
      VALUES (:name, :phone, :licenseNumber, :status)
      RETURNING *
      """;

  private static final String SELECT_BY_ID_SQL = "SELECT * FROM drivers WHERE id = :id";

  private static final String ELIGIBLE_WHERE = "status = 'ACTIVE' AND availability = 'AVAILABLE'";

  private static final String FIND_ELIGIBLE_SQL =
      "SELECT * FROM drivers WHERE "
          + ELIGIBLE_WHERE
          + " ORDER BY name ASC, id LIMIT :limit OFFSET :offset";

  private static final String COUNT_ELIGIBLE_SQL =
      "SELECT count(*) FROM drivers WHERE " + ELIGIBLE_WHERE;

  private static final String UPDATE_AVAILABILITY_SQL =
      "UPDATE drivers SET availability = :availability, updated_at = now() WHERE id = :id RETURNING *";

  private static final String UPDATE_STATUS_SQL =
      "UPDATE drivers SET status = :status, updated_at = now() WHERE id = :id RETURNING *";

  private static final RowMapper<Driver> ROW_MAPPER =
      (rs, rowNum) ->
          new Driver(
              rs.getObject("id", UUID.class),
              rs.getString("name"),
              rs.getString("phone"),
              rs.getString("license_number"),
              DriverAvailability.valueOf(rs.getString("availability")),
              DriverStatus.valueOf(rs.getString("status")),
              rs.getObject("created_at", OffsetDateTime.class),
              rs.getObject("updated_at", OffsetDateTime.class));

  private final NamedParameterJdbcTemplate jdbc;

  public DriverRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Driver insert(String name, String phone, String licenseNumber, DriverStatus status) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("name", name, Types.VARCHAR)
            .addValue("phone", phone, Types.VARCHAR)
            .addValue("licenseNumber", licenseNumber, Types.VARCHAR)
            .addValue("status", status.name(), Types.VARCHAR);
    return jdbc.queryForObject(INSERT_SQL, params, ROW_MAPPER);
  }

  public Optional<Driver> findById(UUID id) {
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id, Types.OTHER);
    return jdbc.query(SELECT_BY_ID_SQL, params, ROW_MAPPER).stream().findFirst();
  }

  public List<Driver> findPage(
      DriverAvailability availability, DriverStatus status, int limit, int offset) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT * FROM drivers");
    appendFilters(sql, params, availability, status);
    sql.append(" ORDER BY name ASC, id LIMIT :limit OFFSET :offset");
    params.addValue("limit", limit).addValue("offset", offset);
    return jdbc.query(sql.toString(), params, ROW_MAPPER);
  }

  public long count(DriverAvailability availability, DriverStatus status) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM drivers");
    appendFilters(sql, params, availability, status);
    Long total = jdbc.queryForObject(sql.toString(), params, Long.class);
    return total != null ? total : 0L;
  }

  public List<Driver> findEligible(int limit, int offset) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset);
    return jdbc.query(FIND_ELIGIBLE_SQL, params, ROW_MAPPER);
  }

  public long countEligible() {
    Long total = jdbc.queryForObject(COUNT_ELIGIBLE_SQL, new MapSqlParameterSource(), Long.class);
    return total != null ? total : 0L;
  }

  public Optional<Driver> updateAvailability(UUID id, DriverAvailability availability) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", id, Types.OTHER)
            .addValue("availability", availability.name(), Types.VARCHAR);
    return jdbc.query(UPDATE_AVAILABILITY_SQL, params, ROW_MAPPER).stream().findFirst();
  }

  public Optional<Driver> updateStatus(UUID id, DriverStatus status) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", id, Types.OTHER)
            .addValue("status", status.name(), Types.VARCHAR);
    return jdbc.query(UPDATE_STATUS_SQL, params, ROW_MAPPER).stream().findFirst();
  }

  private static void appendFilters(
      StringBuilder sql,
      MapSqlParameterSource params,
      DriverAvailability availability,
      DriverStatus status) {
    List<String> conditions = new ArrayList<>();
    if (availability != null) {
      conditions.add("availability = :availability");
      params.addValue("availability", availability.name(), Types.VARCHAR);
    }
    if (status != null) {
      conditions.add("status = :status");
      params.addValue("status", status.name(), Types.VARCHAR);
    }
    if (!conditions.isEmpty()) {
      sql.append(" WHERE ").append(String.join(" AND ", conditions));
    }
  }
}
