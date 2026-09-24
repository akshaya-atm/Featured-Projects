package com.akshaya.elmsbackend.agent;

import com.akshaya.elmsbackend.common.exception.AppException;
import com.google.genai.errors.ApiException;
import com.akshaya.elmsbackend.employee.EmployeeService;
import com.akshaya.elmsbackend.leave.MedicalCertificateService;
import com.akshaya.elmsbackend.tool.CompanyPolicyTools;
import com.akshaya.elmsbackend.tool.EmployeeTools;
import com.akshaya.elmsbackend.tool.ManagerTools;
import com.akshaya.elmsbackend.tool.ToolCallContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class AiAssistantService {

    private final ChatClient chatClient;
    private final EmployeeTools employeeTools;
    private final ManagerTools managerTools;
    private final CompanyPolicyTools companyPolicyTools;
    private final EmployeeService employeeService;
    private final MedicalCertificateService medicalCertificateService;
    private final Clock companyClock;

    public AiAssistantService(
            ChatClient.Builder chatClientBuilder,
            EmployeeTools employeeTools,
            ManagerTools managerTools,
            CompanyPolicyTools companyPolicyTools,
            EmployeeService employeeService,
            MedicalCertificateService medicalCertificateService,
            ChatMemory chatMemory,
            Clock companyClock) {

        this.employeeTools = employeeTools;
        this.managerTools = managerTools;
        this.companyPolicyTools = companyPolicyTools;
        this.employeeService = employeeService;
        this.medicalCertificateService = medicalCertificateService;
        this.companyClock = companyClock;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultSystem("""
                        You are the Employee Leave Assistant for ABC Corp.
                        The current company date is {currentDate}, in the {companyTimeZone} time zone.
                        Resolve "today", "tomorrow", weekdays, and dates without a year against this company date.
                        For a date without a year, use its next occurrence that is not before the company date.
                        Before calling a tool, convert every date to YYYY-MM-DD. Never send relative date words to a tool.
                        Give concise and helpful answers about employee leave.
                        Use the available application tools whenever an employee asks about their personal leave data.
                        For general company leave-policy questions, call search_company_leave_policy and base the
                        answer on the returned policy passages. Do not use policy passages as a source for live
                        balances, request statuses, reporting hierarchy, or assigned approvers.
                        Call apply_leave only when the employee explicitly asks to submit a specific leave request
                        and has supplied the leave type, start date, and end date. Never call it for a policy question,
                        an example, or a hypothetical request. Restate the submitted dates and resulting status.
                        When an employee explicitly asks to upload or replace a medical certificate, identify the
                        correct sick-leave request and call prepare_medical_certificate_upload. Never ask the employee
                        to paste file contents into chat; the application will provide a secure file picker.
                        Use manager tools only for team requests and decisions that the authenticated manager asks you to perform.
                        Do not claim that you accessed employee records or changed leave data
                        unless an application tool actually performed that operation.
                        """)
                .build();
    }

    public AssistantMessageResponse processMessage(
            String employeeId,
            String message,
            String conversationId) {

        if (message == null || message.isBlank()) {
            throw new AppException(
                    AssistantErrorCode.EMPTY_MESSAGE,
                    "Message must not be empty"
            );
        }

        String activeConversationId =
                conversationId == null || conversationId.isBlank()
                        ? UUID.randomUUID().toString()
                        : conversationId.trim();
        String memoryConversationId = employeeId + ":" + activeConversationId;

        boolean manager = employeeService.getEmployeeDetails(employeeId).manager();
        ToolCallContext.DashboardChangeTracker dashboardChangeTracker =
                new ToolCallContext.DashboardChangeTracker();
        ToolCallContext.CertificateUploadTracker certificateUploadTracker =
                new ToolCallContext.CertificateUploadTracker();
        Map<String, Object> toolContext = Map.of(
                ToolCallContext.EMPLOYEE_ID_KEY,
                employeeId,
                ToolCallContext.DASHBOARD_CHANGE_TRACKER_KEY,
                dashboardChangeTracker,
                ToolCallContext.CERTIFICATE_UPLOAD_TRACKER_KEY,
                certificateUploadTracker
        );

        String reply;
        try {
            ChatClient.ChatClientRequestSpec prompt = chatClient.prompt()
                    .system(system -> system
                            .param("currentDate", LocalDate.now(companyClock))
                            .param("companyTimeZone", companyClock.getZone()))
                    .user(message.trim())
                    .advisors(advisor -> advisor.param(
                            ChatMemory.CONVERSATION_ID,
                            memoryConversationId))
                    .tools(employeeTools, companyPolicyTools)
                    .toolContext(toolContext);

            if (manager) {
                prompt.tools(managerTools);
            }

            reply = prompt.call()
                    .content();
        } catch (ToolExecutionException ex) {
            if (ex.getCause() instanceof AppException appException) {
                throw appException;
            }
            throw new AppException(
                    AssistantErrorCode.TOOL_EXECUTION_FAILED,
                    "The requested action could not be completed"
            );
        } catch (TransientAiException | NonTransientAiException | ApiException ex) {
            throw new AppException(
                    AssistantErrorCode.MODEL_UNAVAILABLE,
                    "The assistant is temporarily unavailable"
            );
        }

        if (reply == null || reply.isBlank()) {
            throw new AppException(
                    AssistantErrorCode.INVALID_MODEL_RESPONSE,
                    "The assistant returned an invalid response"
            );
        }

        Long certificateRequestId = certificateUploadTracker.requestedRequestId();
        if (certificateRequestId == null) {
            certificateRequestId = medicalCertificateService
                    .claimNextReminder(employeeId)
                    .orElse(null);
        } else {
            medicalCertificateService.recordReminderShown(
                    employeeId,
                    certificateRequestId);
        }
        return new AssistantMessageResponse(
                reply,
                activeConversationId,
                dashboardChangeTracker.hasChanged(),
                certificateRequestId == null
                        ? null
                        : new AssistantMessageResponse.CertificateUploadAction(
                                certificateRequestId)
        );
    }
}
