package com.akshaya.elmsbackend.agent;

import com.akshaya.elmsbackend.employee.EmployeeService;
import com.akshaya.elmsbackend.employee.entity.EmployeeDetailsResponse;
import com.akshaya.elmsbackend.leave.LeaveApplicationService;
import com.akshaya.elmsbackend.leave.LeaveDecisionService;
import com.akshaya.elmsbackend.leave.LeaveReadService;
import com.akshaya.elmsbackend.leave.MedicalCertificateService;
import com.akshaya.elmsbackend.rag.CompanyPolicyRagService;
import com.akshaya.elmsbackend.tool.CompanyPolicyTools;
import com.akshaya.elmsbackend.tool.EmployeeTools;
import com.akshaya.elmsbackend.tool.ManagerTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAssistantMemoryTest {

    private static final Clock COMPANY_CLOCK = Clock.fixed(
            Instant.parse("2026-09-23T00:00:00Z"),
            ZoneId.of("Asia/Kolkata"));

    @Test
    void remembersPreviousTurnsAndIsolatesMemoryByEmployeeAndConversation() {
        RecordingChatModel model = new RecordingChatModel();
        EmployeeService employeeService = mock(EmployeeService.class);
        LeaveReadService leaveReadService = mock(LeaveReadService.class);
        LeaveApplicationService leaveApplicationService = mock(LeaveApplicationService.class);
        LeaveDecisionService leaveDecisionService = mock(LeaveDecisionService.class);
        MedicalCertificateService medicalCertificateService = mock(MedicalCertificateService.class);
        CompanyPolicyRagService companyPolicyRagService = mock(CompanyPolicyRagService.class);

        when(employeeService.getEmployeeDetails(anyString())).thenAnswer(invocation ->
                new EmployeeDetailsResponse(
                        invocation.getArgument(0),
                        "Employee",
                        "Engineer",
                        "Engineering",
                        "Manager",
                        false));
        when(medicalCertificateService.claimNextReminder(anyString()))
                .thenReturn(Optional.empty());

        EmployeeTools employeeTools = new EmployeeTools(
                leaveReadService,
                employeeService,
                leaveApplicationService,
                medicalCertificateService);
        ManagerTools managerTools = new ManagerTools(
                leaveReadService,
                leaveDecisionService);
        CompanyPolicyTools companyPolicyTools = new CompanyPolicyTools(
                companyPolicyRagService);
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
        AiAssistantService service = new AiAssistantService(
                ChatClient.builder(model),
                employeeTools,
                managerTools,
                companyPolicyTools,
                employeeService,
                medicalCertificateService,
                chatMemory,
                COMPANY_CLOCK);

        service.processMessage("EMP001", "My leave starts Monday", "shared-id");
        service.processMessage("EMP001", "The reason is a medical visit", "shared-id");
        service.processMessage("EMP002", "What did I say before?", "shared-id");
        service.processMessage("EMP001", "This is a separate chat", "other-id");

        List<String> secondPrompt = texts(model.prompts.get(1));
        assertTrue(secondPrompt.stream().anyMatch(text ->
                text.contains("current company date is 2026-09-23")
                        && text.contains("Asia/Kolkata")));
        assertTrue(secondPrompt.contains("My leave starts Monday"));
        assertTrue(secondPrompt.contains("reply-1"));

        List<String> otherEmployeePrompt = texts(model.prompts.get(2));
        assertFalse(otherEmployeePrompt.contains("My leave starts Monday"));
        assertFalse(otherEmployeePrompt.contains("reply-1"));

        List<String> otherConversationPrompt = texts(model.prompts.get(3));
        assertFalse(otherConversationPrompt.contains("My leave starts Monday"));
        assertFalse(otherConversationPrompt.contains("reply-1"));
    }

    private static List<String> texts(Prompt prompt) {
        return prompt.getInstructions().stream()
                .map(message -> message.getText())
                .toList();
    }

    private static final class RecordingChatModel implements ChatModel {
        private final List<Prompt> prompts = new ArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("reply-" + prompts.size()))));
        }
    }
}
