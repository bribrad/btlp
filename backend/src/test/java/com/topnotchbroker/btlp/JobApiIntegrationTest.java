package com.topnotchbroker.btlp;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class JobApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID loadId;

  @BeforeEach
  void setup() {
    jdbcTemplate.update("DELETE FROM jobs");
    jdbcTemplate.update("DELETE FROM loads");
    loadId =
        jdbcTemplate.queryForObject(
            "INSERT INTO loads (origin, destination) VALUES ('Origin', 'Dest') RETURNING id",
            (rs, rowNum) -> rs.getObject(1, UUID.class));
  }

  @Test
  void createReturns201WithDefaultStatusSequenceAndLocation() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/jobs")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(loadId, "PICKUP", null)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.loadId").value(loadId.toString()))
        .andExpect(jsonPath("$.jobType").value("PICKUP"))
        .andExpect(jsonPath("$.sequence").value(1))
        .andExpect(jsonPath("$.status").value("UNASSIGNED"));
  }

  @Test
  void sequenceIsAutoAssignedPerLoad() throws Exception {
    createJob("PICKUP", null);
    mockMvc
        .perform(
            post("/api/v1/jobs")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(loadId, "DROPOFF", null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sequence").value(2));
  }

  @Test
  void createWithUnknownLoadReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/jobs")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(UUID.randomUUID(), "PICKUP", 1)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void listFilteredByLoadIsOrderedBySequence() throws Exception {
    createJob("PICKUP", 2);
    createJob("DROPOFF", 1);
    mockMvc
        .perform(
            get("/api/v1/jobs")
                .param("loadId", loadId.toString())
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[0].sequence").value(1))
        .andExpect(jsonPath("$.content[1].sequence").value(2))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void getByIdReturns200AndUnknownReturns404() throws Exception {
    String id = createJobReturningId("PICKUP", 1);
    mockMvc
        .perform(get("/api/v1/jobs/{id}", id).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.jobType").value("PICKUP"));
    mockMvc
        .perform(
            get("/api/v1/jobs/{id}", UUID.randomUUID())
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void updateChangesMutableFieldsButNotStatusOrLoad() throws Exception {
    String id = createJobReturningId("PICKUP", 1);
    String updateBody = "{\"jobType\":\"DROPOFF\",\"sequence\":5}";
    mockMvc
        .perform(
            put("/api/v1/jobs/{id}", id)
                .with(httpBasic("admin", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobType").value("DROPOFF"))
        .andExpect(jsonPath("$.sequence").value(5))
        .andExpect(jsonPath("$.status").value("UNASSIGNED"))
        .andExpect(jsonPath("$.loadId").value(loadId.toString()));
  }

  @Test
  void duplicateSequenceForLoadReturns409() throws Exception {
    createJob("PICKUP", 1);
    mockMvc
        .perform(
            post("/api/v1/jobs")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(loadId, "DROPOFF", 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("CONFLICT"));
  }

  @Test
  void createMissingJobTypeReturns400() throws Exception {
    String body = "{\"loadId\":\"" + loadId + "\"}";
    mockMvc
        .perform(
            post("/api/v1/jobs")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void driverRoleForbidden() throws Exception {
    mockMvc
        .perform(get("/api/v1/jobs").with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
  }

  @Test
  void anonymousUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/v1/jobs"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  private static String createBody(UUID loadId, String jobType, Integer sequence) {
    String base = "{\"loadId\":\"" + loadId + "\",\"jobType\":\"" + jobType + "\"";
    if (sequence != null) {
      base += ",\"sequence\":" + sequence;
    }
    return base + "}";
  }

  private void createJob(String jobType, Integer sequence) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/jobs")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(loadId, jobType, sequence)))
        .andExpect(status().isCreated());
  }

  private String createJobReturningId(String jobType, Integer sequence) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/jobs")
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody(loadId, jobType, sequence)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
