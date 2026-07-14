package com.topnotchbroker.btlp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.topnotchbroker.btlp.support.PostgresTestContainerConfig;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class DispatchApiIntegrationTest {

  private static final String ENDPOINT = "/api/v1/dispatch/assignments";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID jobId;
  private UUID driverId;

  @BeforeEach
  void setup() {
    jdbcTemplate.update("DELETE FROM assignments");
    jdbcTemplate.update("DELETE FROM jobs");
    jdbcTemplate.update("DELETE FROM loads");
    jdbcTemplate.update("DELETE FROM drivers");

    UUID loadId =
        jdbcTemplate.queryForObject(
            "INSERT INTO loads (origin, destination) VALUES ('Origin', 'Dest') RETURNING id",
            (rs, rowNum) -> rs.getObject(1, UUID.class));

    jobId =
        jdbcTemplate.queryForObject(
            "INSERT INTO jobs (load_id, job_type, sequence, status) VALUES (?, 'PICKUP', 1, 'UNASSIGNED') RETURNING id",
            (rs, rowNum) -> rs.getObject(1, UUID.class),
            loadId);

    driverId =
        jdbcTemplate.queryForObject(
            "INSERT INTO drivers (name, phone, license_number, status, availability) VALUES ('Alice', '555-0100', 'LIC-001', 'ACTIVE', 'AVAILABLE') RETURNING id",
            (rs, rowNum) -> rs.getObject(1, UUID.class));
  }

  @Test
  void dispatchReturns201WithPendingStateAndLocation() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(jobId, driverId)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.jobId").value(jobId.toString()))
        .andExpect(jsonPath("$.driverId").value(driverId.toString()))
        .andExpect(jsonPath("$.state").value("PENDING"))
        .andExpect(jsonPath("$.assignedAt").exists());
  }

  @Test
  void adminCanAlsoDispatch() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(httpBasic("admin", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(jobId, driverId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.state").value("PENDING"));
  }

  @Test
  void secondDispatchToSameJobReturns409() throws Exception {
    // First dispatch succeeds
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(jobId, driverId)))
        .andExpect(status().isCreated());

    // Second dispatch to the same job is a conflict
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(jobId, driverId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("CONFLICT"));
  }

  @Test
  void unknownJobReturns404() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID(), driverId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void unknownDriverReturns404() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(jobId, UUID.randomUUID())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void missingFieldsReturn400() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\":\"" + jobId + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void driverRoleForbidden() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(httpBasic("driver", "driver-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(jobId, driverId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
  }

  @Test
  void anonymousUnauthorized() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(jobId, driverId)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  private static String body(UUID jobId, UUID driverId) {
    return "{\"jobId\":\"" + jobId + "\",\"driverId\":\"" + driverId + "\"}";
  }
}
