package com.topnotchbroker.btlp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.topnotchbroker.btlp.dispatch.AssignmentService;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class AssignmentActionsIntegrationTest {

  private static final String DISPATCH = "/api/v1/dispatch/assignments";
  private static final String DRIVER = "/api/v1/driver/assignments";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AssignmentService assignmentService;

  private UUID jobId;
  private UUID driverId;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    // Keep the background sweeper dormant so expiry is driven explicitly in tests.
    registry.add("btlp.dispatch.expiry-sweep-interval", () -> "PT1H");
    registry.add("btlp.dispatch.assignment-timeout", () -> "PT15M");
  }

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
  void acceptTransitionsAssignmentJobAndDriver() throws Exception {
    UUID assignmentId = dispatch();

    mockMvc
        .perform(post(DRIVER + "/{id}/accept", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ACCEPTED"))
        .andExpect(jsonPath("$.acceptedAt").exists())
        .andExpect(jsonPath("$.expiresAt").exists());

    assertJobStatus("ASSIGNED");
    assertDriverAvailability("ON_TRIP");
  }

  @Test
  void rejectTransitionsAssignmentAndKeepsJobActionable() throws Exception {
    UUID assignmentId = dispatch();

    mockMvc
        .perform(post(DRIVER + "/{id}/reject", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("REJECTED"));

    assertJobStatus("UNASSIGNED");
    assertDriverAvailability("AVAILABLE");
    // Job returned to the actionable queue: a fresh dispatch is allowed.
    mockMvc
        .perform(
            post(DISPATCH)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatchBody()))
        .andExpect(status().isCreated());
  }

  @Test
  void completeTransitionsAssignmentJobAndFreesDriver() throws Exception {
    UUID assignmentId = dispatch();
    accept(assignmentId);

    mockMvc
        .perform(
            post(DRIVER + "/{id}/complete", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("COMPLETED"));

    assertJobStatus("COMPLETED");
    assertDriverAvailability("AVAILABLE");
  }

  @Test
  void secondAcceptReturns409() throws Exception {
    UUID assignmentId = dispatch();
    accept(assignmentId);

    mockMvc
        .perform(post(DRIVER + "/{id}/accept", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void completeWithoutAcceptReturns409() throws Exception {
    UUID assignmentId = dispatch();

    mockMvc
        .perform(
            post(DRIVER + "/{id}/complete", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void unknownAssignmentReturns404() throws Exception {
    mockMvc
        .perform(
            post(DRIVER + "/{id}/accept", UUID.randomUUID()).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void expireStaleTransitionsPendingToExpiredAndReleasesJob() throws Exception {
    UUID assignmentId = dispatch();
    // Simulate the acceptance window elapsing.
    jdbcTemplate.update(
        "UPDATE assignments SET expires_at = now() - interval '1 minute' WHERE id = ?", assignmentId);

    int expired = assignmentService.expireStaleAssignments();

    org.junit.jupiter.api.Assertions.assertEquals(1, expired);
    String state =
        jdbcTemplate.queryForObject(
            "SELECT state FROM assignments WHERE id = ?", String.class, assignmentId);
    org.junit.jupiter.api.Assertions.assertEquals("EXPIRED", state);
    assertJobStatus("UNASSIGNED");
    // Expired assignment frees the job for re-dispatch.
    mockMvc
        .perform(
            post(DISPATCH)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatchBody()))
        .andExpect(status().isCreated());
  }

  @Test
  void acceptAfterDeadlineReturns409() throws Exception {
    UUID assignmentId = dispatch();
    jdbcTemplate.update(
        "UPDATE assignments SET expires_at = now() - interval '1 minute' WHERE id = ?", assignmentId);

    mockMvc
        .perform(post(DRIVER + "/{id}/accept", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void driverActionsEnforceRoleBasedAccess() throws Exception {
    UUID assignmentId = dispatch();

    // Dispatcher lacks the DRIVER role for the driver-scoped endpoint.
    mockMvc
        .perform(
            post(DRIVER + "/{id}/accept", assignmentId)
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));

    // Anonymous is unauthorized.
    mockMvc
        .perform(post(DRIVER + "/{id}/accept", assignmentId))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

    // Admin is allowed.
    mockMvc
        .perform(post(DRIVER + "/{id}/accept", assignmentId).with(httpBasic("admin", "admin-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ACCEPTED"));
  }

  private UUID dispatch() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(DISPATCH)
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(dispatchBody()))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private void accept(UUID assignmentId) throws Exception {
    mockMvc
        .perform(post(DRIVER + "/{id}/accept", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isOk());
  }

  private String dispatchBody() {
    return "{\"jobId\":\"" + jobId + "\",\"driverId\":\"" + driverId + "\"}";
  }

  private void assertJobStatus(String expected) {
    String status =
        jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId);
    org.junit.jupiter.api.Assertions.assertEquals(expected, status);
  }

  private void assertDriverAvailability(String expected) {
    String availability =
        jdbcTemplate.queryForObject(
            "SELECT availability FROM drivers WHERE id = ?", String.class, driverId);
    org.junit.jupiter.api.Assertions.assertEquals(expected, availability);
  }
}
