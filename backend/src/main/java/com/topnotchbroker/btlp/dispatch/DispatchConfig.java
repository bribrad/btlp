package com.topnotchbroker.btlp.dispatch;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@link DispatchProperties} binding and the scheduled assignment-expiry sweep. */
@Configuration
@EnableConfigurationProperties(DispatchProperties.class)
@EnableScheduling
public class DispatchConfig {}
