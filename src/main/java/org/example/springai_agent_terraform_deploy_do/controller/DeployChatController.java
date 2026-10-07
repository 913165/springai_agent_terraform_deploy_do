package org.example.springai_agent_terraform_deploy_do.controller;

import org.example.springai_agent_terraform_deploy_do.tools.DeploymentSimulationTool;
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

    public DeployChatController(ChatClient.Builder chatClientBuilder,
                                DeploymentSimulationTool deploymentSimulationTool) {
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        You are a deployment AI agent. Help the user deploy applications using Terraform.
                        You MUST call the simulateDeployment tool whenever the user asks to deploy,
                        apply, or simulate a deployment. Extract application name, environment, and optional version.
                        After the tool returns, summarize success/failure clearly using the terraform output.
                        """)
                .defaultTools(deploymentSimulationTool)
                .build();
    }

    @GetMapping("/")
    public String chatPage(Model model) {
        if (!model.containsAttribute("messages")) {
            model.addAttribute("messages", new ArrayList<Map<String, String>>());
        }
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
        return "chat";
    }
}
