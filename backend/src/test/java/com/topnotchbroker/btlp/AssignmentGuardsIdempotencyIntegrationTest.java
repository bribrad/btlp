package com.topnotchbroker.btlp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.topnotchbroker.btlp.support.PostgresTestContainerConfig;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
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
class AssignmentGuardsIdempotencyIntegrationTest {

  private static final String DISPATCH = "/api/v1/dispatch/assignments";
  private static final String DRIVER = "/api/v1/driver/assignments";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID jobId;
  private UUID driverId;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
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

  // --- Transition guards -----------------------------------------------------

  @Test
  void acceptThenAcceptIsInvalidTransition() throws Exception {
    UUID id = dispatch(null);
    accept(id, null).andExpect(status().isOk());

    accept(id, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void acceptThenRejectIsInvalidTransition() throws Exception {
    UUID id = dispatch(null);
    accept(id, null).andExpect(status().isOk());

    mockMvc
        .perform(post(DRIVER + "/{id}/reject", id).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void rejectThenAcceptIsInvalidTransition() throws Exception {
    UUID id = dispatch(null);
    mockMvc
        .perform(post(DRIVER + "/{id}/reject", id).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isOk());

    accept(id, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void completeWhilePendingIsInvalidTransition() throws Exception {
    UUID id = dispatch(null);

    mockMvc
        .perform(post(DRIVER + "/{id}/complete", id).with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
  }

  // --- Idempotency -----------------------------------------------------------

  @Test
  void repeatedAcceptWithSameKeyReplaysOriginalResponse() throws Exception {
    UUID id = dispatch(null);

    JsonNode first = json(accept(id, "accept-key-1").andExpect(status().isOk()).andReturn());
    // A replay with the same key returns the original 200 response instead of a 409.
    JsonNode second = json(accept(id, "accept-key-1").andExpect(status().isOk()).andReturn());

    Assertions.assertEquals("ACCEPTED", second.get("state").asText());
    Assertions.assertEquals(first.get("id"), second.get("id"));
    Assertions.assertEquals(first.get("acceptedAt"), second.get("acceptedAt"));
  }

  @Test
  void repeatedDispatchWithSameKeyReturnsSameAssignment() throws Exception {
    JsonNode first = json(dispatchResult("dispatch-key-1").andExpect(status().isCreated()).andReturn());
    // Without the key this second create would 409 (job already has an active assignment);
    // with the same key it replays the original 201 + assignment.
    JsonNode second =
        json(dispatchResult("dispatch-key-1").andExpect(status().isCreated()).andReturn());

    Assertions.assertEquals(first.get("id"), second.get("id"));
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM assignments WHERE job_id = ?", Long.class, jobId);
    Assertions.assertEquals(1L, count);
  }

  @Test
  void reusingKeyForDifferentOperationReturns409() throws Exception {
    UUID id = dispatch(null);
    accept(id, "shared-key").andExpect(status().isOk());

    // Same key, different logical operation (reject vs accept) -> rejected as key reuse.
    mockMvc
        .perform(
            post(DRIVER + "/{id}/reject", id)
                .with(httpBasic("driver", "driver-pass"))
                .header("Idempotency-Key", "shared-key"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("CONFLICT"));
  }

  // --- Helpers ---------------------------------------------------------------

  private UUID dispatch(String idempotencyKey) throws Exception {
    return UUID.fromString(
        json(dispatchResult(idempotencyKey).andExpect(status().isCreated()).andReturn())
            .get("id")
            .asText());
  }

  private org.springframework.test.web.servlet.ResultActions dispatchResult(String idempotencyKey)
      throws Exception {
    var request =
        post(DISPATCH)
            .with(httpBasic("dispatcher", "dispatcher-pass"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"jobId\":\"" + jobId + "\",\"driverId\":\"" + driverId + "\"}");
    if (idempotencyKey != null) {
      request = request.header("Idempotency-Key", idempotencyKey);
    }
    return mockMvc.perform(request);
  }

  private org.springframework.test.web.servlet.ResultActions accept(UUID id, String idempotencyKey)
      throws Exception {
    var request = post(DRIVER + "/{id}/accept", id).with(httpBasic("driver", "driver-pass"));
    if (idempotencyKey != null) {
      request = request.header("Idempotency-Key", idempotencyKey);
    }
    return mockMvc.perform(request);
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }
}
