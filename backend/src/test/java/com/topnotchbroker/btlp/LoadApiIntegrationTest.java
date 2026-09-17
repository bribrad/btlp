package com.topnotchbroker.btlp;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class LoadApiIntegrationTest {

  private static final String CREATE_BODY =
      """
      {"customerId":"ACME","origin":"Chicago, IL","destination":"Dallas, TX",\
      "rateAmount":1250.50,"rateCurrency":"USD","notes":"Fragile"}
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanLoads() {
    // Clear dependants first: rows left by other test classes would block the delete via FK.
    jdbcTemplate.update("DELETE FROM assignments");
    jdbcTemplate.update("DELETE FROM jobs");
    jdbcTemplate.update("DELETE FROM loads");
  }

  @Test
  void createReturns201WithBodyLocationAndAudit() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/loads")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.origin").value("Chicago, IL"))
        .andExpect(jsonPath("$.status").value("PLANNED"))
        .andExpect(jsonPath("$.rateCurrency").value("USD"))
        .andExpect(jsonPath("$.notes").value("Fragile"))
        .andExpect(jsonPath("$.createdBy").value("dispatcher"))
        .andExpect(jsonPath("$.updatedBy").value("dispatcher"));
  }

  @Test
  void getByIdReturnsCreatedLoad() throws Exception {
    String id = createLoad();
    mockMvc
        .perform(get("/api/v1/loads/{id}", id).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.destination").value("Dallas, TX"));
  }

  @Test
  void getByIdUnknownReturns404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/loads/{id}", UUID.randomUUID())
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void listReturnsPagedEnvelope() throws Exception {
    createLoad();
    mockMvc
        .perform(get("/api/v1/loads").with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.page").value(0));
  }

  @Test
  void listFiltersByCaseInsensitiveSearchAcrossOriginDestinationAndCustomer() throws Exception {
    insertLoad("ACME", "Chicago, IL", "Dallas, TX", "PLANNED");
    insertLoad("GLOBEX", "Denver, CO", "Phoenix, AZ", "PLANNED");

    mockMvc
        .perform(
            get("/api/v1/loads")
                .param("q", "chicago")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].origin").value("Chicago, IL"))
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(
            get("/api/v1/loads")
                .param("q", "phoenix")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].destination").value("Phoenix, AZ"));

    mockMvc
        .perform(
            get("/api/v1/loads")
                .param("q", "globex")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].customerId").value("GLOBEX"));
  }

  @Test
  void listSearchMissWithNoMatchesReturnsEmptyPage() throws Exception {
    insertLoad("ACME", "Chicago, IL", "Dallas, TX", "PLANNED");
    mockMvc
        .perform(
            get("/api/v1/loads")
                .param("q", "nowhere")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0));
  }

  @Test
  void listSearchTreatsWildcardsAsLiterals() throws Exception {
    insertLoad("ACME", "Chicago, IL", "Dallas, TX", "PLANNED");
    mockMvc
        .perform(
            get("/api/v1/loads").param("q", "%").with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void listWithBlankSearchIsIgnored() throws Exception {
    insertLoad("ACME", "Chicago, IL", "Dallas, TX", "PLANNED");
    insertLoad("GLOBEX", "Denver, CO", "Phoenix, AZ", "PLANNED");
    mockMvc
        .perform(
            get("/api/v1/loads")
                .param("q", "   ")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void listFiltersByStatus() throws Exception {
    insertLoad("ACME", "Chicago, IL", "Dallas, TX", "PLANNED");
    insertLoad("GLOBEX", "Denver, CO", "Phoenix, AZ", "IN_TRANSIT");

    mockMvc
        .perform(
            get("/api/v1/loads")
                .param("status", "IN_TRANSIT")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].status").value("IN_TRANSIT"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void listCombinesSearchAndStatus() throws Exception {
    insertLoad("ACME", "Chicago, IL", "Dallas, TX", "PLANNED");
    insertLoad("ACME", "Chicago, IL", "Miami, FL", "IN_TRANSIT");

    mockMvc
        .perform(
            get("/api/v1/loads")
                .param("q", "chicago")
                .param("status", "IN_TRANSIT")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].destination").value("Miami, FL"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void listWithUnknownStatusReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/loads")
                .param("status", "NOT_A_STATUS")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message", containsString("status")));
  }

  @Test
  void updateChangesMutableFieldsAndBumpsUpdatedBy() throws Exception {
    String id = createLoad();
    String updateBody =
        """
        {"origin":"Denver, CO","destination":"Dallas, TX","rateAmount":1300.00,\
        "rateCurrency":"EUR","notes":"Updated details"}
        """;
    mockMvc
        .perform(
            put("/api/v1/loads/{id}", id)
                .with(httpBasic("admin", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.origin").value("Denver, CO"))
        .andExpect(jsonPath("$.rateCurrency").value("EUR"))
        .andExpect(jsonPath("$.notes").value("Updated details"))
        .andExpect(jsonPath("$.status").value("PLANNED"))
        .andExpect(jsonPath("$.createdBy").value("dispatcher"))
        .andExpect(jsonPath("$.updatedBy").value("admin"));
  }

  @Test
  void updateUnknownReturns404() throws Exception {
    String updateBody =
        """
        {"origin":"A","destination":"B"}
        """;
    mockMvc
        .perform(
            put("/api/v1/loads/{id}", UUID.randomUUID())
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void createWithBlankOriginReturns400() throws Exception {
    String badBody =
        """
        {"origin":"  ","destination":"Dallas, TX"}
        """;
    mockMvc
        .perform(
            post("/api/v1/loads")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(badBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message", containsString("origin")));
  }

  @Test
  void driverRoleForbidden() throws Exception {
    mockMvc
        .perform(get("/api/v1/loads").with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
  }

  @Test
  void anonymousUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/v1/loads"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  @Test
  void createWithPickupWindowStartAfterEndReturns400() throws Exception {
    String body =
        """
        {"origin":"A","destination":"B",\
        "pickupWindowStart":"2026-01-02T10:00:00Z","pickupWindowEnd":"2026-01-01T10:00:00Z"}
        """;
    mockMvc
        .perform(
            post("/api/v1/loads")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message", containsString("pickupWindowEnd")));
  }

  @Test
  void createWithPickupStartingAfterDropoffReturns400() throws Exception {
    String body =
        """
        {"origin":"A","destination":"B",\
        "pickupWindowStart":"2026-01-03T10:00:00Z","dropoffWindowStart":"2026-01-01T10:00:00Z"}
        """;
    mockMvc
        .perform(
            post("/api/v1/loads")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message", containsString("dropoffWindowStart")));
  }

  @Test
  void createWithWellOrderedWindowsSucceeds() throws Exception {
    String body =
        """
        {"origin":"A","destination":"B",\
        "pickupWindowStart":"2026-01-01T10:00:00Z","pickupWindowEnd":"2026-01-02T10:00:00Z",\
        "dropoffWindowStart":"2026-01-03T10:00:00Z","dropoffWindowEnd":"2026-01-04T10:00:00Z"}
        """;
    mockMvc
        .perform(
            post("/api/v1/loads")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.pickupWindowStart").exists())
        .andExpect(jsonPath("$.status").value("PLANNED"));
  }

  /** Seeds a load directly so tests can pin a status the API does not let callers set. */
  private void insertLoad(String customerId, String origin, String destination, String status) {
    jdbcTemplate.update(
        "INSERT INTO loads (customer_id, origin, destination, status, created_by, updated_by)"
            + " VALUES (?, ?, ?, ?, 'dispatcher', 'dispatcher')",
        customerId,
        origin,
        destination,
        status);
  }

  private String createLoad() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/loads")
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_BODY))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
    return node.get("id").asText();
  }
}
