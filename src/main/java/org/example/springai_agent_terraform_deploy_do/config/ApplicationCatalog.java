package org.example.springai_agent_terraform_deploy_do.config;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-code registry of deployable applications.
 * Put the {@code Map<String, ApplicationConfig>} here.
 * {@code healthUrl} should point at Prometheus/actuator health for that app
 * (e.g. {@code http://host:port/actuator/prometheus} or {@code /actuator/health}).
 */
@Component
public class ApplicationCatalog {

    private final Map<String, ApplicationConfig> applications;

    public ApplicationCatalog() {
        Map<String, ApplicationConfig> apps = new LinkedHashMap<>();
        apps.put("spring-test", new ApplicationConfig(
                "spring-test",
                "spring-test-deployment",
                "Spring Test Application",
                "http://64.225.85.235/actuator/prometheus"
        ));
        apps.put("order-api", new ApplicationConfig(
                "order-api",
                "order-api-deployment",
                "Order API Application",
                "http://64.225.85.235/actuator/prometheus"
        ));
        this.applications = Map.copyOf(apps);
    }

    public Map<String, ApplicationConfig> getApplications() {
        return applications;
    }

    public Collection<ApplicationConfig> values() {
        return applications.values();
    }

    public Optional<ApplicationConfig> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String needle = name.trim();
        ApplicationConfig exact = applications.get(needle);
        if (exact != null) {
            return Optional.of(exact);
        }
        return applications.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(needle))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
