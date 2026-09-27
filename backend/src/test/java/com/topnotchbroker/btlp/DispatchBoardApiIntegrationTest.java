package com.topnotchbroker.btlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Covers the dispatcher-facing board, assignment history, and the cancel/reassign actions that move
 * a job between lanes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class DispatchBoardApiIntegrationTest {

  private static final String BOARD = "/api/v1/dispatch/board";
  private static final String ASSIGNMENTS = "/api/v1/dispatch/assignments";
  private static final String DRIVER_ASSIGNMENTS = "/api/v1/driver/assignments";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID jobId;
  private UUID aliceId;
  private UUID bobId;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    // Keep the background sweeper dormant so lanes only change through the endpoints under test.
    registry.add("btlp.dispatch.expiry-sweep-interval", () -> "PT1H");
    registry.add("btlp.dispatch.assignment-timeout", () -> "PT15M");
  }

  @BeforeEach
  void setup() {
    jdbcTemplate.update("DELETE FROM idempotency_keys");
    jdbcTemplate.update("DELETE FROM assignments");
    jdbcTemplate.update("DELETE FROM jobs");
    jdbcTemplate.update("DELETE FROM loads");
    jdbcTemplate.update("DELETE FROM drivers");

    UUID loadId =
        jdbcTemplate.queryForObject(
            "INSERT INTO loads (origin, destination) VALUES ('Chicago, IL', 'Columbus, OH') RETURNING id",
            (rs, rowNum) -> rs.getObject(1, UUID.class));

    jobId = insertJob(loadId, 1);
    aliceId = insertDriver("Alice Rivera", "555-0100", "LIC-001");
    bobId = insertDriver("Bob Chen", "555-0200", "LIC-002");
  }

  // ── Board ───────────────────────────────────────────────────────────────────

  @Test
  void boardListsUnassignedJobWithItsLoadEndpoints() throws Exception {
    mockMvc
        .perform(get(BOARD).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].jobId").value(jobId.toString()))
        .andExpect(jsonPath("$.content[0].lane").value("NEEDS_DISPATCH"))
        .andExpect(jsonPath("$.content[0].jobStatus").value("UNASSIGNED"))
        .andExpect(jsonPath("$.content[0].origin").value("Chicago, IL"))
        .andExpect(jsonPath("$.content[0].destination").value("Columbus, OH"))
        .andExpect(jsonPath("$.content[0].assignmentId").doesNotExist())
        .andExpect(jsonPath("$.content[0].driverId").doesNotExist());
  }

  @Test
  void dispatchedJobMovesToPendingLaneWithDriverAndActor() throws Exception {
    dispatch(aliceId);

    mockMvc
        .perform(get(BOARD).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].lane").value("PENDING_ACCEPTANCE"))
        .andExpect(jsonPath("$.content[0].assignmentState").value("PENDING"))
        .andExpect(jsonPath("$.content[0].driverName").value("Alice Rivera"))
        .andExpect(jsonPath("$.content[0].driverPhone").value("555-0100"))
        .andExpect(jsonPath("$.content[0].assignedBy").value("dispatcher"))
        .andExpect(jsonPath("$.content[0].expiresAt").exists());
  }

  @Test
  void acceptedAssignmentMovesJobToInProgressLane() throws Exception {
    UUID assignmentId = dispatch(aliceId);
    accept(assignmentId);

    mockMvc
        .perform(get(BOARD).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].lane").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.content[0].assignmentState").value("ACCEPTED"))
        .andExpect(jsonPath("$.content[0].jobStatus").value("ASSIGNED"));
  }

  @Test
  void laneFilterNarrowsTheBoard() throws Exception {
    UUID secondJobId = insertJob(loadIdOf(jobId), 2);
    dispatch(aliceId);

    mockMvc
        .perform(
            get(BOARD)
                .param("lane", "NEEDS_DISPATCH")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].jobId").value(secondJobId.toString()));

    mockMvc
        .perform(
            get(BOARD)
                .param("lane", "PENDING_ACCEPTANCE")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].jobId").value(jobId.toString()));
  }

  @Test
  void finishedJobsAreOffTheBoard() throws Exception {
    jdbcTemplate.update("UPDATE jobs SET status = 'COMPLETED' WHERE id = ?", jobId);

    mockMvc
        .perform(get(BOARD).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void unknownLaneReturns400() throws Exception {
    mockMvc
        .perform(get(BOARD).param("lane", "NOPE").with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  // ── Cancel ──────────────────────────────────────────────────────────────────

  @Test
  void cancelReturnsThePendingJobToTheNeedsDispatchLane() throws Exception {
    UUID assignmentId = dispatch(aliceId);

    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/cancel", assignmentId)
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CANCELED"))
        .andExpect(jsonPath("$.updatedBy").value("dispatcher"));

    assertJobStatus("UNASSIGNED");
    mockMvc
        .perform(get(BOARD).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(jsonPath("$.content[0].lane").value("NEEDS_DISPATCH"));
  }

  @Test
  void cancelAfterAcceptFreesTheJobAndTheDriver() throws Exception {
    UUID assignmentId = dispatch(aliceId);
    accept(assignmentId);

    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/cancel", assignmentId)
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CANCELED"));

    assertJobStatus("UNASSIGNED");
    assertDriverAvailability(aliceId, "AVAILABLE");
  }

  @Test
  void cancelOnAnAlreadyCanceledAssignmentReturns409() throws Exception {
    UUID assignmentId = dispatch(aliceId);
    cancel(assignmentId);

    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/cancel", assignmentId)
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void cancelOfUnknownAssignmentReturns404() throws Exception {
    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/cancel", UUID.randomUUID())
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void cancelReplaysTheOriginalResponseForARetriedIdempotencyKey() throws Exception {
    UUID assignmentId = dispatch(aliceId);

    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc
          .perform(
              post(ASSIGNMENTS + "/{id}/cancel", assignmentId)
                  .header("Idempotency-Key", "cancel-1")
                  .with(httpBasic("dispatcher", "dispatcher-pass")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(assignmentId.toString()))
          .andExpect(jsonPath("$.state").value("CANCELED"));
    }
  }

  // ── Reassign ────────────────────────────────────────────────────────────────

  @Test
  void reassignCancelsTheCurrentAssignmentAndDispatchesTheNewDriver() throws Exception {
    UUID original = dispatch(aliceId);

    MvcResult result =
        mockMvc
            .perform(
                post(ASSIGNMENTS + "/{id}/reassign", original)
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"driverId\":\"" + bobId + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.state").value("PENDING"))
            .andExpect(jsonPath("$.jobId").value(jobId.toString()))
            .andExpect(jsonPath("$.driverId").value(bobId.toString()))
            .andExpect(jsonPath("$.createdBy").value("dispatcher"))
            .andReturn();

    UUID replacement = idOf(result);
    assertEquals("CANCELED", stateOf(original));
    assertEquals("PENDING", stateOf(replacement));

    mockMvc
        .perform(get(BOARD).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].lane").value("PENDING_ACCEPTANCE"))
        .andExpect(jsonPath("$.content[0].driverName").value("Bob Chen"));
  }

  @Test
  void reassignAfterAcceptFreesThePreviousDriver() throws Exception {
    UUID original = dispatch(aliceId);
    accept(original);

    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/reassign", original)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\":\"" + bobId + "\"}"))
        .andExpect(status().isCreated());

    assertDriverAvailability(aliceId, "AVAILABLE");
    // The job waits on Bob's answer again, so it is back to UNASSIGNED until he accepts.
    assertJobStatus("UNASSIGNED");
  }

  @Test
  void jobHistoryExposesEveryAssignmentAndItsActor() throws Exception {
    UUID original = dispatch(aliceId);
    reassign(original, bobId);

    mockMvc
        .perform(
            get(ASSIGNMENTS)
                .param("jobId", jobId.toString())
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        // Newest first: the replacement leads, the canceled original follows.
        .andExpect(jsonPath("$.content[0].driverId").value(bobId.toString()))
        .andExpect(jsonPath("$.content[0].state").value("PENDING"))
        .andExpect(jsonPath("$.content[0].createdBy").value("dispatcher"))
        .andExpect(jsonPath("$.content[1].id").value(original.toString()))
        .andExpect(jsonPath("$.content[1].state").value("CANCELED"))
        .andExpect(jsonPath("$.content[1].updatedBy").value("dispatcher"));
  }

  @Test
  void reassignAuditsBothTheCanceledAndTheNewAssignment() throws Exception {
    UUID original = dispatch(aliceId);
    UUID replacement = reassign(original, bobId);

    assertEquals(1, auditCount(original, "CANCEL"));
    assertEquals(1, auditCount(replacement, "REASSIGN"));
  }

  @Test
  void reassignToAnUnknownDriverReturns404AndLeavesTheAssignmentIntact() throws Exception {
    UUID original = dispatch(aliceId);

    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/reassign", original)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));

    assertEquals("PENDING", stateOf(original));
  }

  @Test
  void reassignWithoutDriverIdReturns400() throws Exception {
    UUID original = dispatch(aliceId);

    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/reassign", original)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void reassignOfACanceledAssignmentReturns409() throws Exception {
    UUID original = dispatch(aliceId);
    cancel(original);

    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/reassign", original)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\":\"" + bobId + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void reassignReplaysTheOriginalResponseForARetriedIdempotencyKey() throws Exception {
    UUID original = dispatch(aliceId);
    String body = "{\"driverId\":\"" + bobId + "\"}";

    MvcResult first =
        mockMvc
            .perform(
                post(ASSIGNMENTS + "/{id}/reassign", original)
                    .header("Idempotency-Key", "reassign-1")
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/reassign", original)
                .header("Idempotency-Key", "reassign-1")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(idOf(first).toString()));

    // The retry replayed the stored response instead of dispatching a third assignment.
    assertEquals(
        2,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM assignments WHERE job_id = ?", Integer.class, jobId));
  }

  // ── Assignment list filters ─────────────────────────────────────────────────

  @Test
  void assignmentListFiltersByDriverAndState() throws Exception {
    UUID original = dispatch(aliceId);
    reassign(original, bobId);

    mockMvc
        .perform(
            get(ASSIGNMENTS)
                .param("driverId", aliceId.toString())
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(original.toString()));

    mockMvc
        .perform(
            get(ASSIGNMENTS)
                .param("state", "PENDING")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].driverId").value(bobId.toString()));
  }

  // ── RBAC ────────────────────────────────────────────────────────────────────

  @Test
  void driverRoleIsForbiddenFromTheBoardAndItsActions() throws Exception {
    UUID assignmentId = dispatch(aliceId);

    mockMvc
        .perform(get(BOARD).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));

    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/cancel", assignmentId).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
  }

  @Test
  void anonymousIsUnauthorized() throws Exception {
    mockMvc
        .perform(get(BOARD))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  @Test
  void adminCanReadTheBoardAndReassign() throws Exception {
    UUID original = dispatch(aliceId);

    mockMvc
        .perform(get(BOARD).with(httpBasic("admin", "admin-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].lane").value("PENDING_ACCEPTANCE"));

    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/reassign", original)
                .with(httpBasic("admin", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\":\"" + bobId + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.createdBy").value("admin"));
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private UUID dispatch(UUID driverId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(ASSIGNMENTS)
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"jobId\":\"" + jobId + "\",\"driverId\":\"" + driverId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(result);
  }

  private UUID reassign(UUID assignmentId, UUID driverId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(ASSIGNMENTS + "/{id}/reassign", assignmentId)
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"driverId\":\"" + driverId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(result);
  }

  private void cancel(UUID assignmentId) throws Exception {
    mockMvc
        .perform(
            post(ASSIGNMENTS + "/{id}/cancel", assignmentId)
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk());
  }

  private void accept(UUID assignmentId) throws Exception {
    mockMvc
        .perform(
            post(DRIVER_ASSIGNMENTS + "/{id}/accept", assignmentId)
                .with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isOk());
  }

  private UUID idOf(MvcResult result) throws Exception {
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID insertJob(UUID loadId, int sequence) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO jobs (load_id, job_type, sequence, status) VALUES (?, 'PICKUP', ?, 'UNASSIGNED') RETURNING id",
        (rs, rowNum) -> rs.getObject(1, UUID.class),
        loadId,
        sequence);
  }

  private UUID insertDriver(String name, String phone, String license) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO drivers (name, phone, license_number, status, availability) VALUES (?, ?, ?, 'ACTIVE', 'AVAILABLE') RETURNING id",
        (rs, rowNum) -> rs.getObject(1, UUID.class),
        name,
        phone,
        license);
  }

  private UUID loadIdOf(UUID job) {
    return jdbcTemplate.queryForObject(
        "SELECT load_id FROM jobs WHERE id = ?", UUID.class, job);
  }

  private String stateOf(UUID assignmentId) {
    return jdbcTemplate.queryForObject(
        "SELECT state FROM assignments WHERE id = ?", String.class, assignmentId);
  }

  private int auditCount(UUID assignmentId, String action) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM audit_events WHERE entity_type = 'ASSIGNMENT' AND entity_id = ? AND action = ?",
        Integer.class,
        assignmentId,
        action);
  }

  private void assertJobStatus(String expected) {
    assertEquals(
        expected,
        jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId));
  }

  private void assertDriverAvailability(UUID driverId, String expected) {
    assertEquals(
        expected,
        jdbcTemplate.queryForObject(
            "SELECT availability FROM drivers WHERE id = ?", String.class, driverId));
  }
}
