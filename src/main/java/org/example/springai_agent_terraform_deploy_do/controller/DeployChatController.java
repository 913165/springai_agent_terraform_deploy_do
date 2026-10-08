package org.example.springai_agent_terraform_deploy_do.controller;

import org.example.springai_agent_terraform_deploy_do.config.ApplicationCatalog;
import org.example.springai_agent_terraform_deploy_do.tools.ApplicationCatalogTool;
import org.example.springai_agent_terraform_deploy_do.tools.DeploymentSimulationTool;
import org.example.springai_agent_terraform_deploy_do.tools.JenkinsDeploymentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@SessionAttributes("messages")
public class DeployChatController {

    private static final Logger log = LoggerFactory.getLogger(DeployChatController.class);

    private final ChatClient chatClient;
    private final ApplicationCatalog applicationCatalog;

    public DeployChatController(ChatClient.Builder chatClientBuilder,
                                DeploymentSimulationTool deploymentSimulationTool,
                                JenkinsDeploymentTool jenkinsDeploymentTool,
                                ApplicationCatalogTool applicationCatalogTool,
                                ApplicationCatalog applicationCatalog) {
        this.applicationCatalog = applicationCatalog;
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        You are a deployment AI agent. Help the user deploy applications.
                        - Use listApplications when the user asks which apps exist or which Jenkins jobs are available.
                        - Use simulateDeployment for direct Kubernetes/Terraform deploys (terraform/ directory).
                        - Use deployViaJenkins when the user asks to deploy via Jenkins AND the message indicates
                          the user already approved (e.g. contains "approved" or is an explicit approved deploy).
                          Pass the registered application name (e.g. spring-test, order-api).
                        - If the user has not approved yet, do NOT call deploy tools; remind them to Approve or Cancel
                          in the chat confirmation UI.
                        Extract application name and any optional job/environment/version details.
                        After a tool returns, summarize success/failure clearly using the tool output.
                        """)
                .defaultTools(deploymentSimulationTool, jenkinsDeploymentTool, applicationCatalogTool)
                .build();
    }

    @GetMapping({"/", "/chat"})
    public String chatPage(Model model) {
        if (!model.containsAttribute("messages")) {
            model.addAttribute("messages", new ArrayList<Map<String, String>>());
        }
        addApplications(model);
        return "chat";
    }

    @PostMapping("/chat")
    public String chat(@RequestParam String message, Model model) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) model.getAttribute("messages");
        if (messages == null) {
            messages = new ArrayList<>();
        }

        messages.add(Map.of("role", "user", "content", message));
        System.out.println(">>> CHAT user message: " + message);
        log.info(">>> CHAT user message: {}", message);

        String reply = chatClient
                .prompt()
                .user(message)
                .call()
                .content();

        System.out.println(">>> CHAT assistant reply: " + reply);
        log.info(">>> CHAT assistant reply: {}", reply);
        messages.add(Map.of("role", "assistant", "content", reply != null ? reply : "(no response)"));
        model.addAttribute("messages", messages);
        addApplications(model);
        return "chat";
    }

    private void addApplications(Model model) {
        model.addAttribute("applications", applicationCatalog.values());
    }
}
