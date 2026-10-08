package org.example.springai_agent_terraform_deploy_do.tools;

import org.example.springai_agent_terraform_deploy_do.config.ApplicationCatalog;
import org.example.springai_agent_terraform_deploy_do.config.ApplicationConfig;
import org.example.springai_agent_terraform_deploy_do.service.ApplicationHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class JenkinsDeploymentTool {

    private static final Logger log = LoggerFactory.getLogger(JenkinsDeploymentTool.class);

    private final ApplicationCatalog applicationCatalog;
    private final ApplicationHealthService applicationHealthService;

    @Value("${app.jenkins.terraform.dir:jenkins_dir}")
    private String jenkinsTerraformDir;

    @Value("${app.terraform.executable:terraform}")
    private String terraformExecutable;

    @Value("${app.terraform.timeout-minutes:15}")
    private long timeoutMinutes;

    public JenkinsDeploymentTool(ApplicationCatalog applicationCatalog,
                                 ApplicationHealthService applicationHealthService) {
        this.applicationCatalog = applicationCatalog;
        this.applicationHealthService = applicationHealthService;
    }

    @Tool(description = "Trigger a Jenkins deployment by running terraform apply in the jenkins_dir directory. Resolves the Jenkins job from registered ApplicationConfig by application name. If Prometheus health is DOWN, approved must be true — never rollback and never deploy a DOWN app without approval.")
    public String deployViaJenkins(
            @ToolParam(description = "Application or service name being deployed (must match a registered application name)") String applicationName,
            @ToolParam(description = "Must be true when the user clicked Approve in the UI (required if app health is DOWN)", required = false) Boolean approved,
            @ToolParam(description = "Optional Jenkins job name override", required = false) String jobName) {

        boolean isApproved = Boolean.TRUE.equals(approved);
        System.out.println("============================================================");
        System.out.println(">>> TOOL deployViaJenkins START");
        System.out.println(">>> timestamp=" + Instant.now());
        System.out.println(">>> applicationName=" + applicationName);
        System.out.println(">>> approved=" + isApproved);
        System.out.println(">>> jobName(raw)=" + jobName);
        System.out.println(">>> configured jenkinsTerraformDir=" + jenkinsTerraformDir);
        System.out.println(">>> configured terraformExecutable=" + terraformExecutable);
        System.out.println(">>> configured timeoutMinutes=" + timeoutMinutes);
        System.out.println(">>> user.dir=" + System.getProperty("user.dir"));
        System.out.println("============================================================");
        log.info(">>> TOOL deployViaJenkins START application={}, approved={}, job={}, dir={}, executable={}",
                applicationName, isApproved, jobName, jenkinsTerraformDir, terraformExecutable);

        String resolvedJob = resolveJenkinsJob(applicationName, jobName);
        if (resolvedJob == null) {
            String known = applicationCatalog.values().stream()
                    .map(ApplicationConfig::name)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(none)");
            String err = "Unknown application '%s'. Registered apps: %s. Use listApplications to see details."
                    .formatted(applicationName, known);
            System.out.println(">>> TOOL deployViaJenkins ERROR: " + err);
            log.error(err);
            return err;
        }
        System.out.println(">>> resolvedJob=" + resolvedJob);

        ApplicationConfig app = applicationCatalog.findByName(applicationName).orElse(null);
        if (app != null) {
            ApplicationHealthService.HealthStatus health = applicationHealthService.checkOne(app);
            System.out.println(">>> prometheusHealth=" + health.status() + " detail=" + health.detail());
            if ("DOWN".equalsIgnoreCase(health.status()) && !isApproved) {
                String err = """
                        BLOCKED: application '%s' Prometheus health is DOWN (%s).
                        No deploy/rollback was started.
                        Ask the user to Approve in the chat UI, then call again with approved=true.
                        """.formatted(applicationName, health.detail()).trim();
                System.out.println(">>> TOOL deployViaJenkins ERROR: " + err);
                log.warn(err);
                return err;
            }
            if (!isApproved) {
                String err = """
                        BLOCKED: deploy requires explicit user approval.
                        No deploy/rollback was started.
                        Ask the user to Approve in the chat UI, then call again with approved=true.
                        """.trim();
                System.out.println(">>> TOOL deployViaJenkins ERROR: " + err);
                log.warn(err);
                return err;
            }
        }

        File workingDir = resolveJenkinsDir();
        System.out.println(">>> resolved workingDir=" + workingDir.getAbsolutePath());
        System.out.println(">>> workingDir.exists=" + workingDir.exists());
        System.out.println(">>> workingDir.isDirectory=" + workingDir.isDirectory());

        if (!workingDir.isDirectory()) {
            String err = "Jenkins terraform directory not found: " + workingDir.getAbsolutePath();
            System.out.println(">>> TOOL deployViaJenkins ERROR: " + err);
            log.error(err);
            return err;
        }

        File tfvars = new File(workingDir, "terraform.tfvars");
        File mainTf = new File(workingDir, "main.tf");
        System.out.println(">>> main.tf exists=" + mainTf.exists() + " path=" + mainTf.getAbsolutePath());
        System.out.println(">>> terraform.tfvars exists=" + tfvars.exists() + " path=" + tfvars.getAbsolutePath());

        try {
            System.out.println(">>> STEP: starting terraform apply ...");
            log.info(">>> STEP: starting terraform apply in {} job={}", workingDir.getAbsolutePath(), resolvedJob);

            CommandResult applyResult = runTerraformApply(workingDir, resolvedJob);

            System.out.println(">>> STEP: terraform apply finished");
            System.out.println(">>> exitCode=" + applyResult.exitCode());
            System.out.println(">>> outputChars=" + applyResult.output().length());
            log.info(">>> STEP: terraform apply finished exitCode={} outputChars={}",
                    applyResult.exitCode(), applyResult.output().length());

            if (applyResult.exitCode() != 0) {
                String msg = """
                        Jenkins terraform deployment failed.
                        application=%s
                        job=%s
                        status=FAILED
                        exitCode=%d
                        workingDir=%s
                        timestamp=%s

                        --- terraform apply output ---
                        %s
                        """.formatted(
                        applicationName,
                        resolvedJob,
                        applyResult.exitCode(),
                        workingDir.getAbsolutePath(),
                        Instant.now(),
                        applyResult.output()
                ).trim();
                System.out.println(">>> TOOL deployViaJenkins RESULT: FAILED");
                System.out.println(msg);
                log.error(msg);
                return msg;
            }

            String result = """
                    Jenkins terraform deployment successful.
                    application=%s
                    job=%s
                    status=SUCCESS
                    exitCode=%d
                    workingDir=%s
                    timestamp=%s

                    --- terraform apply output ---
                    %s
                    """.formatted(
                    applicationName,
                    resolvedJob,
                    applyResult.exitCode(),
                    workingDir.getAbsolutePath(),
                    Instant.now(),
                    applyResult.output()
            ).trim();

            System.out.println(">>> TOOL deployViaJenkins RESULT: SUCCESS");
            System.out.println(result);
            System.out.println("============================================================");
            log.info(">>> TOOL deployViaJenkins RESULT: SUCCESS");
            return result;
        } catch (Exception e) {
            String err = "Jenkins terraform execution error: " + e.getMessage();
            System.out.println(">>> TOOL deployViaJenkins ERROR: " + err);
            e.printStackTrace(System.out);
            log.error("Jenkins terraform execution failed", e);
            return err;
        }
    }

    private String resolveJenkinsJob(String applicationName, String jobNameOverride) {
        if (jobNameOverride != null && !jobNameOverride.isBlank()) {
            return jobNameOverride.trim();
        }
        return applicationCatalog.findByName(applicationName)
                .map(ApplicationConfig::jenkinsJob)
                .orElse(null);
    }

    private File resolveJenkinsDir() {
        System.out.println(">>> resolveJenkinsDir: input=" + jenkinsTerraformDir);
        File dir = new File(jenkinsTerraformDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), jenkinsTerraformDir);
            System.out.println(">>> resolveJenkinsDir: made absolute -> " + dir.getAbsolutePath());
        } else {
            System.out.println(">>> resolveJenkinsDir: already absolute -> " + dir.getAbsolutePath());
        }
        return dir;
    }

    private CommandResult runTerraformApply(File workingDir, String jenkinsJob)
            throws IOException, InterruptedException {

        List<String> command = new ArrayList<>();
        command.add(terraformExecutable);
        command.add("apply");
        command.add("-auto-approve");
        command.add("-var");
        command.add("jenkins_job=" + jenkinsJob);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir);
        processBuilder.redirectErrorStream(true);

        System.out.println("------------------------------------------------------------");
        System.out.println(">>> ProcessBuilder.command=" + processBuilder.command());
        System.out.println(">>> ProcessBuilder.directory=" + workingDir.getAbsolutePath());
        System.out.println(">>> ProcessBuilder.redirectErrorStream=true");
        System.out.println(">>> about to processBuilder.start()");
        System.out.println("------------------------------------------------------------");
        log.info("Running {} in {}", processBuilder.command(), workingDir.getAbsolutePath());

        Process process = processBuilder.start();
        System.out.println(">>> process started, pid=" + process.pid() + ", alive=" + process.isAlive());
        System.out.println(">>> reading terraform stdout/stderr (merged) ...");

        StringBuilder output = new StringBuilder();
        int lineCount = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                System.out.println("[terraform] " + line);
                if (!output.isEmpty()) {
                    output.append('\n');
                }
                output.append(line);
            }
        }

        System.out.println(">>> finished reading stream, lines=" + lineCount);
        System.out.println(">>> waiting for process exit (timeoutMinutes=" + timeoutMinutes + ") ...");

        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        System.out.println(">>> waitFor finished=" + finished + ", alive=" + process.isAlive());

        if (!finished) {
            System.out.println(">>> TIMEOUT: destroying process forcibly");
            process.destroyForcibly();
            throw new IOException("Terraform timed out after " + timeoutMinutes + " minutes");
        }

        int exitCode = process.exitValue();
        System.out.println("Terraform exit code: " + exitCode);
        log.info("Jenkins terraform apply finished with exitCode={}", exitCode);

        String text = output.toString().isBlank() ? "(no output)" : output.toString().trim();
        System.out.println(">>> captured output blank=" + output.toString().isBlank());
        return new CommandResult(exitCode, text);
    }

    private record CommandResult(int exitCode, String output) {
    }
}
