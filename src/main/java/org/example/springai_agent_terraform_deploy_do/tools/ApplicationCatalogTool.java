package org.example.springai_agent_terraform_deploy_do.tools;

import org.example.springai_agent_terraform_deploy_do.config.ApplicationCatalog;
import org.example.springai_agent_terraform_deploy_do.config.ApplicationConfig;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.stream.Collectors;

@Component
public class ApplicationCatalogTool {

    private final ApplicationCatalog applicationCatalog;

    public ApplicationCatalogTool(ApplicationCatalog applicationCatalog) {
        this.applicationCatalog = applicationCatalog;
    }

    @Tool(description = "List registered applications with their Jenkins jobs and descriptions. Use when the user asks which apps can be deployed.")
    public String listApplications() {
        Collection<ApplicationConfig> apps = applicationCatalog.values();
        if (apps.isEmpty()) {
            return "No applications are registered in configuration.";
        }

        String body = apps.stream()
                .map(app -> "- name=%s | jenkinsJob=%s | description=%s"
                        .formatted(app.name(), app.jenkinsJob(), app.description()))
                .collect(Collectors.joining("\n"));

        System.out.println(">>> TOOL listApplications:\n" + body);
        return "Registered applications:\n" + body;
    }
}
