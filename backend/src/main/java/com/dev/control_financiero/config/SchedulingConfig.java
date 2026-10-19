package com.dev.control_financiero.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Migration jobs must never import mail or deliver reminders. */
@Configuration
@Profile("!migration")
@EnableScheduling
public class SchedulingConfig { }
