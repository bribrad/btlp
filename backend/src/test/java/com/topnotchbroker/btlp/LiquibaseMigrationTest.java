package com.topnotchbroker.btlp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.topnotchbroker.btlp.support.PostgresTestContainerConfig;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Validates the Liquibase migration set against a real PostgreSQL (Testcontainers):
 *
 * <ul>
 *   <li>migrations apply cleanly (all core tables are created on startup),
 *   <li>core foreign keys and status CHECK constraints are enforced,
 *   <li>the rollback path cleanly reverts every change and re-applies without error.
 * </ul>
 */
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
class LiquibaseMigrationTest {

  private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

  private static final String[] EXPECTED_TABLES = {
    "drivers",
    "loads",
    "jobs",
    "assignments",
    "job_status_events",
    "export_runs",
    "billing_records",
    "billing_record_jobs"
  };

  @Autowired private DataSource dataSource;

  /** Guarantee the schema is present for sibling tests/classes sharing the container. */
  @AfterEach
  void restoreSchema() throws Exception {
    update();
  }

  @Test
  void migrationsApplyAllCoreTables() throws SQLException {
    for (String table : EXPECTED_TABLES) {
      assertThat(tableExists(table)).as("table '%s' exists after migration", table).isTrue();
    }
  }

  @Test
  void statusCheckConstraintIsEnforced() throws SQLException {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement()) {
      assertThatThrownBy(() -> s.executeUpdate("INSERT INTO loads (status) VALUES ('NOT_A_STATUS')"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void foreignKeyConstraintIsEnforced() throws SQLException {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement()) {
      assertThatThrownBy(
              () ->
                  s.executeUpdate(
                      "INSERT INTO jobs (load_id, job_type, sequence) "
                          + "VALUES ('00000000-0000-0000-0000-000000000000', 'PICKUP', 1)"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void rollbackRevertsEveryChangeThenReapplies() throws Exception {
    int applied = countAppliedChangeSets();
    assertThat(applied).isGreaterThanOrEqualTo(EXPECTED_TABLES.length);

    rollback(applied);
    for (String table : EXPECTED_TABLES) {
      assertThat(tableExists(table)).as("table '%s' dropped after rollback", table).isFalse();
    }

    update();
    for (String table : EXPECTED_TABLES) {
      assertThat(tableExists(table)).as("table '%s' recreated after re-update", table).isTrue();
    }
  }

  private void update() throws Exception {
    try (Liquibase liquibase = newLiquibase()) {
      liquibase.update(new Contexts(), new LabelExpression());
    }
  }

  private void rollback(int count) throws Exception {
    try (Liquibase liquibase = newLiquibase()) {
      liquibase.rollback(count, new Contexts(), new LabelExpression());
    }
  }

  private Liquibase newLiquibase() throws SQLException, DatabaseException {
    Database database =
        DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(new JdbcConnection(dataSource.getConnection()));
    return new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
  }

  private int countAppliedChangeSets() throws SQLException {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private boolean tableExists(String table) throws SQLException {
    try (Connection c = dataSource.getConnection();
        ResultSet rs = c.getMetaData().getTables(null, "public", table, new String[] {"TABLE"})) {
      return rs.next();
    }
  }
}
