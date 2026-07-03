package com.topnotchbroker.btlp.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ApiAuthenticationEntryPoint authenticationEntryPoint,
      ApiAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable);
    http.httpBasic(Customizer.withDefaults());
    http.exceptionHandling(
        exceptions ->
            exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));
    http.authorizeHttpRequests(
        authz ->
            authz.requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/dispatch/**").hasAnyRole("DISPATCHER", "ADMIN")
                .requestMatchers("/api/v1/loads/**").hasAnyRole("DISPATCHER", "ADMIN")
                .requestMatchers("/api/v1/jobs/**").hasAnyRole("DISPATCHER", "ADMIN")
                .requestMatchers("/api/v1/audit/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/driver/**").hasAnyRole("DRIVER", "ADMIN")
                .requestMatchers("/api/v1/billing/**").hasAnyRole("BILLING", "ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/me").authenticated()
                .anyRequest()
                .authenticated());
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
    return new InMemoryUserDetailsManager(
        User.withUsername("dispatcher")
            .password(passwordEncoder.encode("dispatcher-pass"))
            .roles("DISPATCHER")
            .build(),
        User.withUsername("driver")
            .password(passwordEncoder.encode("driver-pass"))
            .roles("DRIVER")
            .build(),
        User.withUsername("billing")
            .password(passwordEncoder.encode("billing-pass"))
            .roles("BILLING")
            .build(),
        User.withUsername("admin")
            .password(passwordEncoder.encode("admin-pass"))
            .roles("ADMIN")
            .build());
  }
}
