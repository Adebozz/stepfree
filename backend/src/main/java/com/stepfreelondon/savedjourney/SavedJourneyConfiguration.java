package com.stepfreelondon.savedjourney;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SavedJourneyProperties.class)
class SavedJourneyConfiguration {
}
