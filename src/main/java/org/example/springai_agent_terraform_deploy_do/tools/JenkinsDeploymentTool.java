package org.example.springai_agent_terraform_deploy_do.tools;

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
import java.util.concurrent.TimeUnit;

@Component
public class JenkinsDeploymentTool {

    private static final Logger log = LoggerFactory.getLogger(JenkinsDeploymentTool.class);

    @Value("${app.jenkins.terraform.dir:jenkins_dir}")
    private String jenkinsTerraformDir;

    @Value("${app.terraform.executable:terraform}")
    private String terraformExecutable;

    @Value("${app.terraform.timeout-minutes:15}")
    private long timeoutMinutes;

    @Tool(description = "Trigger a Jenkins deployment by running terraform apply in the jenkins_dir directory. Use when the user asks to deploy via Jenkins or trigger a Jenkins job.")
    public String deployViaJenkins(
            @ToolParam(description = "Application or service name being deployed") String applicationName,
            @ToolParam(description = "Optional Jenkins job name override", required = false) String jobName) {

        System.out.println("============================================================");
        System.out.println(">>> TOOL deployViaJenkins START");
        System.out.println(">>> timestamp=" + Instant.now());
        System.out.println(">>> applicationName=" + applicationName);
        System.out.println(">>> jobName(raw)=" + jobName);
        System.out.println(">>> configured jenkinsTerraformDir=" + jenkinsTerraformDir);
        System.out.println(">>> configured terraformExecutable=" + terraformExecutable);
        System.out.println(">>> configured timeoutMinutes=" + timeoutMinutes);
        System.out.println(">>> user.dir=" + System.getProperty("user.dir"));
        System.out.println("============================================================");
        log.info(">>> TOOL deployViaJenkins START application={}, job={}, dir={}, executable={}",
                applicationName, jobName, jenkinsTerraformDir, terraformExecutable);

        String resolvedJob = (jobName == null || jobName.isBlank()) ? "(from terraform.tfvars)" : jobName.trim();
        System.out.println(">>> resolvedJob=" + resolvedJob);

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
            log.info(">>> STEP: starting terraform apply in {}", workingDir.getAbsolutePath());

            CommandResult applyResult = runTerraformApply(workingDir);

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

    private CommandResult runTerraformApply(File workingDir) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                terraformExecutable,
                "apply",
                "-auto-approve"
        );
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
