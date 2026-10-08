package org.example.springai_agent_terraform_deploy_do.config;

public record ApplicationConfig(
        String name,
        String jenkinsJob,
        String description,
        String healthUrl
) {
}
