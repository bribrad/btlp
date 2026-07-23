package com.topnotchbroker.btlp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end coverage of the dispatch and assignment lifecycle exercised entirely through the public
 * HTTP API: create (load + job + driver) &rarr; dispatch &rarr; accept/reject/complete/expire, plus
 * transition-guard, idempotency, and RBAC behavior. Mirrors the documented M1 readiness demo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class DispatchLifecycleIntegrationTest {

  private static final String LOADS = "/api/v1/loads";
  private static final String JOBS = "/api/v1/jobs";
  private static final String DRIVERS = "/api/v1/drivers";
  private static final String DISPATCH = "/api/v1/dispatch/assignments";
  private static final String DRIVER = "/api/v1/driver/assignments";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AssignmentService assignmentService;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("btlp.dispatch.expiry-sweep-interval", () -> "PT1H");
    registry.add("btlp.dispatch.assignment-timeout", () -> "PT15M");
  }

  @BeforeEach
  void clean() {
    jdbcTemplate.update("DELETE FROM idempotency_keys");
    jdbcTemplate.update("DELETE FROM assignments");
    jdbcTemplate.update("DELETE FROM jobs");
    jdbcTemplate.update("DELETE FROM loads");
    jdbcTemplate.update("DELETE FROM drivers");
  }

  @Test
  void createDispatchAcceptCompleteHappyPath() throws Exception {
    UUID jobId = createJob(createLoad());
    UUID driverId = createDriver();
    UUID assignmentId = dispatch(jobId, driverId, null);

    // Dispatcher can view the pending assignment (resolves the create Location).
    mockMvc
        .perform(get(DISPATCH + "/{id}", assignmentId).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("PENDING"))
        .andExpect(jsonPath("$.expiresAt").exists());

    // Driver accepts.
    mockMvc
        .perform(post(DRIVER + "/{id}/accept", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ACCEPTED"))
        .andExpect(jsonPath("$.acceptedAt").exists());
    assertJobStatus(jobId, "ASSIGNED");
    assertDriverAvailability(driverId, "ON_TRIP");

    // Driver completes.
    mockMvc
        .perform(post(DRIVER + "/{id}/complete", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("COMPLETED"));
    assertJobStatus(jobId, "COMPLETED");
    assertDriverAvailability(driverId, "AVAILABLE");
  }

  @Test
  void rejectReturnsJobToActionableQueue() throws Exception {
    UUID jobId = createJob(createLoad());
    UUID driverId = createDriver();
    UUID assignmentId = dispatch(jobId, driverId, null);

    mockMvc
        .perform(post(DRIVER + "/{id}/reject", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("REJECTED"));
    assertJobStatus(jobId, "UNASSIGNED");

    // Re-dispatch is allowed once the job is actionable again.
    dispatch(jobId, driverId, null);
  }

  @Test
  void expireReturnsJobToActionableQueue() throws Exception {
    UUID jobId = createJob(createLoad());
    UUID driverId = createDriver();
    UUID assignmentId = dispatch(jobId, driverId, null);

    jdbcTemplate.update(
        "UPDATE assignments SET expires_at = now() - interval '1 minute' WHERE id = ?", assignmentId);
    int expired = assignmentService.expireStaleAssignments();
    org.junit.jupiter.api.Assertions.assertEquals(1, expired);

    mockMvc
        .perform(get(DISPATCH + "/{id}", assignmentId).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("EXPIRED"));
    assertJobStatus(jobId, "UNASSIGNED");
    dispatch(jobId, driverId, null);
  }

  @Test
  void idempotentAcceptReplayReturnsSameResponse() throws Exception {
    UUID jobId = createJob(createLoad());
    UUID driverId = createDriver();
    UUID assignmentId = dispatch(jobId, driverId, null);

    JsonNode first =
        json(
            mockMvc
                .perform(
                    post(DRIVER + "/{id}/accept", assignmentId)
                        .with(httpBasic("driver", "driver-pass"))
                        .header("Idempotency-Key", "lifecycle-accept-1"))
                .andExpect(status().isOk())
                .andReturn());
    JsonNode replay =
        json(
            mockMvc
                .perform(
                    post(DRIVER + "/{id}/accept", assignmentId)
                        .with(httpBasic("driver", "driver-pass"))
                        .header("Idempotency-Key", "lifecycle-accept-1"))
                .andExpect(status().isOk())
                .andReturn());
    org.junit.jupiter.api.Assertions.assertEquals(first.get("acceptedAt"), replay.get("acceptedAt"));
  }

  @Test
  void repeatedAcceptWithoutKeyIsInvalidTransition() throws Exception {
    UUID jobId = createJob(createLoad());
    UUID driverId = createDriver();
    UUID assignmentId = dispatch(jobId, driverId, null);
    mockMvc
        .perform(post(DRIVER + "/{id}/accept", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isOk());

    mockMvc
        .perform(post(DRIVER + "/{id}/accept", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void roleBasedAccessAcrossDispatchAndDriverScopes() throws Exception {
    UUID jobId = createJob(createLoad());
    UUID driverId = createDriver();

    // Driver role cannot dispatch.
    mockMvc
        .perform(
            post(DISPATCH)
                .with(httpBasic("driver", "driver-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatchBody(jobId, driverId)))
        .andExpect(status().isForbidden());

    UUID assignmentId = dispatch(jobId, driverId, null);

    // Dispatcher role cannot act on the driver-scoped endpoint.
    mockMvc
        .perform(
            post(DRIVER + "/{id}/accept", assignmentId)
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isForbidden());

    // Anonymous cannot dispatch.
    mockMvc
        .perform(post(DISPATCH).contentType(MediaType.APPLICATION_JSON).content(dispatchBody(jobId, driverId)))
        .andExpect(status().isUnauthorized());
  }

  // --- Helpers ---------------------------------------------------------------

  private UUID createLoad() throws Exception {
    return idFrom(
        mockMvc
            .perform(
                post(LOADS)
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"origin\":\"Origin\",\"destination\":\"Dest\"}"))
            .andExpect(status().isCreated()));
  }

  private UUID createJob(UUID loadId) throws Exception {
    return idFrom(
        mockMvc
            .perform(
                post(JOBS)
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"loadId\":\"" + loadId + "\",\"jobType\":\"PICKUP\"}"))
            .andExpect(status().isCreated()));
  }

  private UUID createDriver() throws Exception {
    return idFrom(
        mockMvc
            .perform(
                post(DRIVERS)
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Alice\",\"phone\":\"555-0100\",\"licenseNumber\":\"LIC-001\"}"))
            .andExpect(status().isCreated()));
  }

  private UUID dispatch(UUID jobId, UUID driverId, String idempotencyKey) throws Exception {
    var request =
        post(DISPATCH)
            .with(httpBasic("dispatcher", "dispatcher-pass"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(dispatchBody(jobId, driverId));
    if (idempotencyKey != null) {
      request = request.header("Idempotency-Key", idempotencyKey);
    }
    return idFrom(mockMvc.perform(request).andExpect(status().isCreated()));
  }

  private static String dispatchBody(UUID jobId, UUID driverId) {
    return "{\"jobId\":\"" + jobId + "\",\"driverId\":\"" + driverId + "\"}";
  }

  private void assertJobStatus(UUID jobId, String expected) throws Exception {
    mockMvc
        .perform(get(JOBS + "/{id}", jobId).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(expected));
  }

  private void assertDriverAvailability(UUID driverId, String expected) throws Exception {
    mockMvc
        .perform(get(DRIVERS + "/{id}", driverId).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availability").value(expected));
  }

  private UUID idFrom(ResultActions actions) throws Exception {
    return UUID.fromString(json(actions.andReturn()).get("id").asText());
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }
}
