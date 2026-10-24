package com.stepfreelondon.tfl;

import java.net.http.HttpClient;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TflProperties.class)
class TflClientConfiguration {

    /**
     * Uses the Boot-provided {@link RestClient.Builder} (Jackson converters, observability) and adds
     * explicit timeouts. Without them a hung TfL connection would hold a request thread indefinitely.
     */
    @Bean
    TflClient tflClient(RestClient.Builder builder, TflProperties properties, MeterRegistry meterRegistry) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return new TflClient(builder.requestFactory(requestFactory), properties, meterRegistry, Sleeper.THREAD_SLEEP);
    }
}
