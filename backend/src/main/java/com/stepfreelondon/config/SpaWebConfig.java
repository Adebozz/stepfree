package com.stepfreelondon.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the React build (copied into {@code classpath:/static} by the Docker build) from the same
 * origin as the API, so session cookies stay first-party (risk R2).
 *
 * <p>Client-side routes such as {@code /saved} have no file, so they get {@code index.html} and React
 * Router takes over. API, actuator and docs paths never fall back: an unknown {@code /api/...} must stay
 * a 404, not become a 200 HTML page.
 */
@Configuration(proxyBeanMethods = false)
class SpaWebConfig implements WebMvcConfigurer {

    private static final String[] NO_FALLBACK = {"api/", "actuator", "v3/", "swagger-ui", "internal/"};

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (isClientRoute(resourcePath)) {
                            Resource index = new ClassPathResource("/static/index.html");
                            return index.exists() ? index : null;
                        }
                        return null;
                    }
                });
    }

    /** Extension-less paths outside the server's own prefixes: {@code saved}, {@code journey/0}. */
    static boolean isClientRoute(String path) {
        for (String prefix : NO_FALLBACK) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".");
    }
}
