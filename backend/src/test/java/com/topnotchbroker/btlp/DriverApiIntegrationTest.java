package com.topnotchbroker.btlp;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class DriverApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clean() {
    jdbcTemplate.update("DELETE FROM drivers");
  }

  @Test
  void createReturns201WithDefaults() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/drivers")
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice\",\"phone\":\"555-1000\",\"licenseNumber\":\"DL-1\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("Alice"))
        .andExpect(jsonPath("$.availability").value("AVAILABLE"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void getByIdReturns200AndUnknownReturns404() throws Exception {
    String id = createDriver("Bob");
    mockMvc
        .perform(get("/api/v1/drivers/{id}", id).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Bob"));
    mockMvc
        .perform(
            get("/api/v1/drivers/{id}", UUID.randomUUID())
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void availabilityToggleIsPersisted() throws Exception {
    String id = createDriver("Carol");
    mockMvc
        .perform(
            patch("/api/v1/drivers/{id}/availability", id)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"availability\":\"UNAVAILABLE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availability").value("UNAVAILABLE"));
    mockMvc
        .perform(get("/api/v1/drivers/{id}", id).with(httpBasic("admin", "admin-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availability").value("UNAVAILABLE"));
  }

  @Test
  void statusToggleIsPersisted() throws Exception {
    String id = createDriver("Dan");
    mockMvc
        .perform(
            patch("/api/v1/drivers/{id}/status", id)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
    mockMvc
        .perform(get("/api/v1/drivers/{id}", id).with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
  }

  @Test
  void availabilityToggleToOnTripIsRejected() throws Exception {
    String id = createDriver("Erin");
    mockMvc
        .perform(
            patch("/api/v1/drivers/{id}/availability", id)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"availability\":\"ON_TRIP\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void eligibleExcludesUnavailableInactiveAndOnTrip() throws Exception {
    String available = createDriver("Available Amy"); // ACTIVE + AVAILABLE => eligible
    String unavailable = createDriver("Unavailable Uma");
    setAvailability(unavailable, "UNAVAILABLE");
    String inactive = createDriver("Inactive Ian");
    setStatus(inactive, "INACTIVE");
    // ON_TRIP cannot be set via the API, so seed it directly.
    jdbcTemplate.update(
        "INSERT INTO drivers (name, availability, status) VALUES ('OnTrip Ora', 'ON_TRIP', 'ACTIVE')");

    mockMvc
        .perform(get("/api/v1/drivers/eligible").with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(available))
        .andExpect(jsonPath("$.content[0].name").value("Available Amy"));
  }

  @Test
  void listCanFilterByStatus() throws Exception {
    createDriver("Active Ann");
    String inactive = createDriver("Inactive Ivy");
    setStatus(inactive, "INACTIVE");
    mockMvc
        .perform(
            get("/api/v1/drivers")
                .param("status", "INACTIVE")
                .with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].name").value("Inactive Ivy"))
        .andExpect(jsonPath("$.content[0].status").value("INACTIVE"));
  }

  @Test
  void driverRoleForbiddenAndAnonymousUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/v1/drivers").with(httpBasic("driver", "driver-pass")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    mockMvc
        .perform(get("/api/v1/drivers"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
  }

  private String createDriver(String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/drivers")
                    .with(httpBasic("dispatcher", "dispatcher-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private void setAvailability(String id, String availability) throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/drivers/{id}/availability", id)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"availability\":\"" + availability + "\"}"))
        .andExpect(status().isOk());
  }

  private void setStatus(String id, String status) throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/drivers/{id}/status", id)
                .with(httpBasic("dispatcher", "dispatcher-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"))
        .andExpect(status().isOk());
  }
}
