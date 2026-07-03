package com.topnotchbroker.btlp;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.topnotchbroker.btlp.support.PostgresTestContainerConfig;
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
class AuditApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clean() {
    jdbcTemplate.update("DELETE FROM audit_events");
    jdbcTemplate.update("DELETE FROM jobs");
    jdbcTemplate.update("DELETE FROM loads");
  }

  @Test
  void loadCreateAndUpdateEmitAuditEntriesWithActorAndAction() throws Exception {
    String loadId = createLoad();
    mockMvc
        .perform(
            put("/api/v1/loads/{id}", loadId)
                .with(httpBasic("admin", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"origin\":\"C\",\"destination\":\"D\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/audit")
                .param("entityType", "LOAD")
                .param("entityId", loadId)
                .with(httpBasic("admin", "admin-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].entityType").value("LOAD"))
        .andExpect(jsonPath("$.content[0].entityId").value(loadId))
        .andExpect(jsonPath("$.content[0].action").value("UPDATE"))
        .andExpect(jsonPath("$.content[0].actor").value("admin"))
        .andExpect(jsonPath("$.content[0].occurredAt").exists())
        .andExpect(jsonPath("$.content[1].action").value("CREATE"))
        .andExpect(jsonPath("$.content[1].actor").value("dispatcher"));
  }

  @Test
  void jobCreateEmitsAuditEntry() throws Exception {
    String loadId = createLoad();
    mockMvc
        .perform(
            post("/api/v1/jobs")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loadId\":\"" + loadId + "\",\"jobType\":\"PICKUP\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/v1/audit")
                .param("entityType", "JOB")
                .with(httpBasic("admin", "admin-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].entityType").value("JOB"))
        .andExpect(jsonPath("$.content[0].action").value("CREATE"))
        .andExpect(jsonPath("$.content[0].actor").value("dispatcher"));
  }

  @Test
  void auditRetrievalRequiresAdmin() throws Exception {
    mockMvc
        .perform(get("/api/v1/audit").with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    mockMvc
        .perform(get("/api/v1/audit").with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/audit"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  private String createLoad() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/loads")
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"origin\":\"A\",\"destination\":\"B\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
