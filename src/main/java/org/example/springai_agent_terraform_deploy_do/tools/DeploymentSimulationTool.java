package org.example.springai_agent_terraform_deploy_do.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class DeploymentSimulationTool {

    private static final Logger log = LoggerFactory.getLogger(DeploymentSimulationTool.class);

    @Tool(description = "Simulate a cloud/application deployment. Does not perform a real deploy; returns a fake deployment result for demos and testing.")
    public String simulateDeployment(
            @ToolParam(description = "Application or service name to deploy") String applicationName,
            @ToolParam(description = "Target environment, e.g. dev, staging, or prod") String environment,
            @ToolParam(description = "Optional version or image tag to deploy", required = false) String version) {

        String resolvedVersion = (version == null || version.isBlank()) ? "latest" : version.trim();
        String deploymentId = "dep-" + UUID.randomUUID().toString().substring(0, 8);

        String callMsg = ">>> TOOL simulateDeployment CALLED: application=%s, environment=%s, version=%s, deploymentId=%s"
                .formatted(applicationName, environment, resolvedVersion, deploymentId);
        System.out.println(callMsg);
        log.info(callMsg);

        String result = """
                Deployment simulation completed.
                deploymentId=%s
                application=%s
                environment=%s
                version=%s
                status=SUCCESS
                message=Simulated rollout finished with no real infrastructure changes.
                timestamp=%s
                """.formatted(
                deploymentId,
                applicationName,
                environment,
                resolvedVersion,
                Instant.now()
        ).trim();

        String resultMsg = ">>> TOOL simulateDeployment RESULT: " + result;
        System.out.println(resultMsg);
        log.info(resultMsg);
        return result;
    }
}
