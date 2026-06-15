package com.topnotchbroker.btlp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRbacIntegrationTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void protectedEndpointRequiresAuthenticatedIdentity() throws Exception {
    mockMvc
        .perform(get("/api/v1/dispatch/jobs"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
        .andExpect(
            jsonPath("$.message").value("Authentication is required to access this resource."));
  }

  @Test
  void dispatcherRoleCanAccessDispatcherEndpoint() throws Exception {
    mockMvc
        .perform(get("/api/v1/dispatch/jobs").with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Dispatch queue access granted."));
  }

  @Test
  void dispatcherRoleCannotAccessBillingEndpoint() throws Exception {
    mockMvc
        .perform(get("/api/v1/billing/exports").with(httpBasic("dispatcher", "dispatcher-pass")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"))
        .andExpect(
            jsonPath("$.message")
                .value("Authenticated user is not allowed to access this resource."));
  }

  @Test
  void adminRoleCanAccessAllProtectedRoleEndpoints() throws Exception {
    mockMvc
        .perform(get("/api/v1/dispatch/jobs").with(httpBasic("admin", "admin-pass")))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/driver/assignments").with(httpBasic("admin", "admin-pass")))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/billing/exports").with(httpBasic("admin", "admin-pass")))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/admin/users").with(httpBasic("admin", "admin-pass")))
        .andExpect(status().isOk());
  }
}
