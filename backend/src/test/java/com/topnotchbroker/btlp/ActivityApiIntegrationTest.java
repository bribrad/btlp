package com.topnotchbroker.btlp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.topnotchbroker.btlp.support.PostgresTestContainerConfig;
import java.util.ArrayList;
import java.util.List;
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

/**
 * Covers the operations timeline endpoint: the who/what/when it reports, the stability of its
 * ordering, load- and job-scoped filtering, and the role matrix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class ActivityApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clean() {
    jdbcTemplate.update("DELETE FROM audit_events");
    jdbcTemplate.update("DELETE FROM assignments");
    jdbcTemplate.update("DELETE FROM jobs");
    jdbcTemplate.update("DELETE FROM loads");
    jdbcTemplate.update("DELETE FROM drivers");
  }

  @Test
  void timelineReportsActorActionAndTimeForEachEvent() throws Exception {
    String loadId = createLoad("Dallas", "Austin");

    mockMvc
        .perform(get("/api/v1/activity").with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.content[0].actor").value("dispatcher"))
        .andExpect(jsonPath("$.content[0].action").value("CREATE"))
        .andExpect(jsonPath("$.content[0].entityType").value("LOAD"))
        .andExpect(jsonPath("$.content[0].entityId").value(loadId))
        .andExpect(jsonPath("$.content[0].detail").value("PLANNED"))
        .andExpect(jsonPath("$.content[0].occurredAt").exists())
        .andExpect(jsonPath("$.content[0].sequence", greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.content[0].loadId").value(loadId))
        .andExpect(jsonPath("$.content[0].origin").value("Dallas"))
        .andExpect(jsonPath("$.content[0].destination").value("Austin"));
  }

  @Test
  void dispatchTransitionsAreNamedRatherThanReportedAsGenericUpdates() throws Exception {
    String loadId = createLoad("Dallas", "Austin");
    String jobId = createJob(loadId);
    String driverId = createDriver("Alice");
    String assignmentId = dispatch(jobId, driverId);
    accept(assignmentId);

    List<String> actions = actionsFor("jobId", jobId);

    // Newest first: accept writes the job status change before the assignment transition.
    assertThat(actions).containsExactly("ACCEPT", "STATUS_CHANGE", "ASSIGN", "CREATE");
  }

  @Test
  void assignmentEventsCarryTheJobLegAndTheDriverTheyConcern() throws Exception {
    String loadId = createLoad("Dallas", "Austin");
    String jobId = createJob(loadId);
    String driverId = createDriver("Alice");
    dispatch(jobId, driverId);

    mockMvc
        .perform(
            get("/api/v1/activity")
                .param("jobId", jobId)
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].action").value("ASSIGN"))
        .andExpect(jsonPath("$.content[0].entityType").value("ASSIGNMENT"))
        .andExpect(jsonPath("$.content[0].driverId").value(driverId))
        .andExpect(jsonPath("$.content[0].driverName").value("Alice"))
        .andExpect(jsonPath("$.content[0].jobId").value(jobId))
        .andExpect(jsonPath("$.content[0].jobType").value("PICKUP"))
        .andExpect(jsonPath("$.content[0].jobSequence").value(1))
        .andExpect(jsonPath("$.content[0].loadId").value(loadId));
  }

  @Test
  void loadFilterIncludesEventsOnItsJobsAndAssignments() throws Exception {
    String loadId = createLoad("Dallas", "Austin");
    String jobId = createJob(loadId);
    String driverId = createDriver("Alice");
    dispatch(jobId, driverId);
    createLoad("Reno", "Boise");

    // Load created, job created, assignment dispatched — but nothing from the unrelated load.
    mockMvc
        .perform(
            get("/api/v1/activity")
                .param("loadId", loadId)
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content", hasSize(3)));

    assertThat(actionsFor("jobId", jobId)).containsExactly("ASSIGN", "CREATE");
  }

  @Test
  void unknownLoadReturnsAnEmptyPageRatherThanEverything() throws Exception {
    createLoad("Dallas", "Austin");

    mockMvc
        .perform(
            get("/api/v1/activity")
                .param("loadId", UUID.randomUUID().toString())
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void orderIsIdenticalOnEveryRead() throws Exception {
    String loadId = createLoad("Dallas", "Austin");
    String jobId = createJob(loadId);
    String driverId = createDriver("Alice");
    String assignmentId = dispatch(jobId, driverId);
    accept(assignmentId);

    List<String> first = idsFor(loadId);
    List<String> second = idsFor(loadId);
    List<String> third = idsFor(loadId);

    // The cascade writes several events inside one transaction, so they share occurred_at; the
    // insertion sequence is what keeps them from shuffling between reads.
    assertThat(first).hasSize(5).isEqualTo(second).isEqualTo(third);
  }

  @Test
  void pageSizeIsClampedAndPagingWalksTheSameOrder() throws Exception {
    String loadId = createLoad("Dallas", "Austin");
    createJob(loadId);

    List<String> all = idsFor(loadId);

    MvcResult firstPage =
        mockMvc
            .perform(
                get("/api/v1/activity")
                    .param("loadId", loadId)
                    .param("size", "1000")
                    .param("page", "0")
                    .with(httpBasic("dispatcher", "dispatcher-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(100))
            .andReturn();
    assertThat(ids(firstPage)).isEqualTo(all);

    mockMvc
        .perform(
            get("/api/v1/activity")
                .param("loadId", loadId)
                .param("size", "1")
                .param("page", "1")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.content[0].id").value(all.get(1)));
  }

  @Test
  void malformedLoadIdReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/activity")
                .param("loadId", "not-a-uuid")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void timelineIsLimitedToDispatchersAndAdmins() throws Exception {
    mockMvc
        .perform(get("/api/v1/activity").with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/activity").with(httpBasic("admin", "admin-pass")))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/activity").with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    mockMvc
        .perform(get("/api/v1/activity").with(httpBasic("billing", "billing-pass")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    mockMvc
        .perform(get("/api/v1/activity"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  private List<String> actionsFor(String param, String value) throws Exception {
    return field(fetch(param, value), "action");
  }

  private List<String> idsFor(String loadId) throws Exception {
    return field(fetch("loadId", loadId), "id");
  }

  private List<String> ids(MvcResult result) throws Exception {
    return field(result, "id");
  }

  private MvcResult fetch(String param, String value) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/activity")
                .param(param, value)
                .param("size", "100")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andReturn();
  }

  private List<String> field(MvcResult result, String name) throws Exception {
    JsonNode content =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
    List<String> values = new ArrayList<>();
    content.forEach(node -> values.add(node.get(name).asText()));
    return values;
  }

  private String createLoad(String origin, String destination) throws Exception {
    return idOf(
        mockMvc
            .perform(
                post("/api/v1/loads")
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"origin\":\"" + origin + "\",\"destination\":\"" + destination + "\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private String createJob(String loadId) throws Exception {
    return idOf(
        mockMvc
            .perform(
                post("/api/v1/jobs")
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"loadId\":\"" + loadId + "\",\"jobType\":\"PICKUP\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private String createDriver(String name) throws Exception {
    return idOf(
        mockMvc
            .perform(
                post("/api/v1/drivers")
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\""
                            + name
                            + "\",\"phone\":\"555-0100\",\"licenseNumber\":\"TX-"
                            + name
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private String dispatch(String jobId, String driverId) throws Exception {
    return idOf(
        mockMvc
            .perform(
                post("/api/v1/dispatch/assignments")
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"jobId\":\"" + jobId + "\",\"driverId\":\"" + driverId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private void accept(String assignmentId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/driver/assignments/{id}/accept", assignmentId)
                .with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isOk());
  }

  private String idOf(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
