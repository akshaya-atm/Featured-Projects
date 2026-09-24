package com.akshaya.elmsbackend.tool;

import com.akshaya.elmsbackend.leave.LeaveDecisionService;
import com.akshaya.elmsbackend.leave.LeaveReadService;
import com.akshaya.elmsbackend.leave.dto.LeaveResponses.Requests;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ManagerTools {

    private final LeaveReadService leaveReadService;
    private final LeaveDecisionService leaveDecisionService;

    public ManagerTools(
            LeaveReadService leaveReadService,
            LeaveDecisionService leaveDecisionService) {

        this.leaveReadService = leaveReadService;
        this.leaveDecisionService = leaveDecisionService;
    }

    @Tool(
            name = "list_pending_team_leave_requests",
            description = "List pending direct-manager or second-level approval stages currently assigned to the authenticated manager"
    )
    public Requests listPendingTeamLeaveRequests(ToolContext toolContext) {
        return leaveReadService.getPendingTeamRequests(
                ToolCallContext.requireEmployeeId(toolContext)
        );
    }

    @Tool(
            name = "approve_leave_request",
            description = "Approve the current pending leave-approval stage assigned to the authenticated manager"
    )
    public LeaveDecisionService.DecisionResult approveLeaveRequest(
            @ToolParam(description = "ID of the leave request to approve") Long requestId,
            @ToolParam(
                    description = "Optional note explaining the approval",
                    required = false
            ) String reason,
            ToolContext toolContext) {

        LeaveDecisionService.DecisionResult result = leaveDecisionService.decide(
                ToolCallContext.requireEmployeeId(toolContext),
                requestId,
                true,
                reason
        );
        ToolCallContext.markDashboardChanged(toolContext);
        return result;
    }

    @Tool(
            name = "reject_leave_request",
            description = "Reject the current pending leave-approval stage assigned to the authenticated manager"
    )
    public LeaveDecisionService.DecisionResult rejectLeaveRequest(
            @ToolParam(description = "ID of the leave request to reject") Long requestId,
            @ToolParam(
                    description = "Optional note explaining the rejection",
                    required = false
            ) String reason,
            ToolContext toolContext) {

        LeaveDecisionService.DecisionResult result = leaveDecisionService.decide(
                ToolCallContext.requireEmployeeId(toolContext),
                requestId,
                false,
                reason
        );
        ToolCallContext.markDashboardChanged(toolContext);
        return result;
    }
}
