package com.akshaya.elmsbackend.tool;

import com.akshaya.elmsbackend.employee.EmployeeService;
import com.akshaya.elmsbackend.employee.entity.EmployeeDetailsResponse;
import com.akshaya.elmsbackend.leave.LeaveApplicationService;
import com.akshaya.elmsbackend.leave.LeaveReadService;
import com.akshaya.elmsbackend.leave.MedicalCertificateService;
import com.akshaya.elmsbackend.leave.dto.LeaveResponses.Balances;
import com.akshaya.elmsbackend.leave.dto.LeaveResponses.Requests;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class EmployeeTools {

    private final LeaveReadService leaveReadService;
    private final EmployeeService employeeService;
    private final LeaveApplicationService leaveApplicationService;
    private final MedicalCertificateService medicalCertificateService;

    public EmployeeTools(
            LeaveReadService leaveReadService,
            EmployeeService employeeService,
            LeaveApplicationService leaveApplicationService,
            MedicalCertificateService medicalCertificateService) {

        this.leaveReadService = leaveReadService;
        this.employeeService = employeeService;
        this.leaveApplicationService = leaveApplicationService;
        this.medicalCertificateService = medicalCertificateService;
    }

    @Tool(
            name = "get_employee_profile",
            description = "Get the authenticated employee's profile, reporting manager, department, and manager status"
    )
    public EmployeeDetailsResponse getEmployeeProfile(ToolContext toolContext) {
        return employeeService.getEmployeeDetails(
                ToolCallContext.requireEmployeeId(toolContext)
        );
    }

    @Tool(
            name = "get_leave_balances",
            description = "Get the authenticated employee's annual, casual, and sick leave balances for a year"
    )
    public Balances getLeaveBalances(
            @ToolParam(
                    description = "Balance year. Omit to use the current company year.",
                    required = false
            ) Integer year,
            ToolContext toolContext) {

        String employeeId = ToolCallContext.requireEmployeeId(toolContext);
        return leaveReadService.getBalances(employeeId, year);
    }

    @Tool(
            name = "list_my_leave_requests",
            description = "List the authenticated employee's leave requests, including status, dates, reason, and medical certificate state"
    )
    public Requests listMyLeaveRequests(ToolContext toolContext) {
        return leaveReadService.getOwnRequests(
                ToolCallContext.requireEmployeeId(toolContext)
        );
    }

    @Tool(
            name = "apply_leave",
            description = "Submit a specific leave request for the authenticated employee. Use only when the employee explicitly asks to apply, not for policy questions, examples, or hypothetical dates."
    )
    public LeaveApplicationService.ApplicationResult applyLeave(
            @ToolParam(description = "Leave type: ANNUAL, CASUAL, SICK, or UNPAID")
            String leaveType,
            @ToolParam(description = "Absolute first leave date in YYYY-MM-DD format; never use a relative date word")
            LocalDate startDate,
            @ToolParam(description = "Absolute last leave date in YYYY-MM-DD format; never use a relative date word")
            LocalDate endDate,
            @ToolParam(
                    description = "Optional reason for the leave request",
                    required = false)
            String reason,
            ToolContext toolContext) {

        LeaveApplicationService.ApplicationResult result = leaveApplicationService.apply(
                ToolCallContext.requireEmployeeId(toolContext),
                leaveType,
                startDate,
                endDate,
                reason);
        ToolCallContext.markDashboardChanged(toolContext);
        if (result.medicalCertificateRequired()) {
            ToolCallContext.requestCertificateUpload(toolContext, result.id());
        }
        return result;
    }

    @Tool(
            name = "prepare_medical_certificate_upload",
            description = "Prepare a browser file-upload action for a specific sick-leave request owned by the authenticated employee. Use only when the employee explicitly asks to upload or replace a medical certificate."
    )
    public MedicalCertificateService.UploadPreparation prepareMedicalCertificateUpload(
            @ToolParam(description = "ID of the sick-leave request receiving the certificate")
            Long requestId,
            ToolContext toolContext) {

        MedicalCertificateService.UploadPreparation preparation =
                medicalCertificateService.prepareUpload(
                        ToolCallContext.requireEmployeeId(toolContext),
                        requestId);
        ToolCallContext.requestCertificateUpload(toolContext, preparation.requestId());
        return preparation;
    }
}
