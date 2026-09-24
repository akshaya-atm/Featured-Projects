package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.employee.entity.Employee;
import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import com.akshaya.elmsbackend.leave.entity.*;
import com.akshaya.elmsbackend.leave.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.akshaya.elmsbackend.common.exception.AppException;
import com.akshaya.elmsbackend.common.exception.AppErrorCode;
import com.akshaya.elmsbackend.employee.EmployeeErrorCode;
import java.time.*;
import java.util.*;

@Service
public class LeaveDecisionService {
    private final EmployeeRepository employees;
    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final LeaveApprovalRepository approvals;
    private final CompanyHolidayRepository holidays;
    private final Clock companyClock;

    public LeaveDecisionService(EmployeeRepository employees, LeaveRequestRepository requests,
                                LeaveBalanceRepository balances, LeaveApprovalRepository approvals,
                                CompanyHolidayRepository holidays,
                                Clock companyClock) {
        this.employees = employees;
        this.requests = requests;
        this.balances = balances;
        this.approvals = approvals;
        this.holidays = holidays;
        this.companyClock = companyClock;
    }

    @Transactional
    public DecisionResult decide(String actorId, Long requestId, boolean approved, String reason) {
        if (reason != null && reason.length() > 500) {
            throw error(LeaveErrorCode.INVALID_REASON_LENGTH, "Decision reason must be at most 500 characters");
        }
        Employee actor = employees.findByEmployeeId(actorId)
                .orElseThrow(() -> error(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "Employee account is unavailable"));
        if (!actor.isActive()) throw error(EmployeeErrorCode.EMPLOYEE_INACTIVE, "Employee account is inactive");

        // Serialize decisions for this request so duplicate clicks cannot debit leave twice.
        LeaveRequest request = requests.findForDecision(requestId)
                .orElseThrow(() -> error(LeaveErrorCode.REQUEST_NOT_FOUND, "Leave request not found"));
        Employee owner = employees.findForDecision(request.getEmployee().getId())
                .orElseThrow(() -> error(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "Employee details not found"));
        if (!owner.isActive()) {
            throw error(EmployeeErrorCode.EMPLOYEE_INACTIVE, "Employee account is inactive");
        }
        if (owner.getId().equals(actor.getId())) {
            throw error(LeaveErrorCode.NOT_YOUR_TEAM, "An employee cannot approve their own leave request");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw error(LeaveErrorCode.ALREADY_DECIDED, "This request has already been decided");
        }

        Instant now = Instant.now(companyClock);
        List<LeaveApproval> stages = approvals.findByRequest_IdOrderBySequenceNo(requestId);
        LeaveApproval stage = stages.stream()
                .filter(approval -> !"APPROVED".equals(approval.getStatus()))
                .findFirst()
                .orElseThrow(() -> error(
                        LeaveErrorCode.NO_PENDING_APPROVAL,
                        "No pending approval stage"));
        if (!"PENDING".equals(stage.getStatus())
                || stage.getApprover() == null
                || !stage.getApprover().getId().equals(actor.getId())) {
            throw error(
                    LeaveErrorCode.STAGE_NOT_ASSIGNED,
                    "This approval stage is not assigned to you");
        }

        boolean finalApproval = approved
                && stages.get(stages.size() - 1) == stage;
        if (!approved || finalApproval) settleBalance(request, owner, approved, now);
        stage.decide(approved ? "APPROVED" : "REJECTED", reason == null ? null : reason.trim(), now);
        approvals.save(stage);
        String status = !approved ? "REJECTED" : finalApproval ? "APPROVED" : "PENDING";
        request.decide(status, now);
        requests.save(request);
        return new DecisionResult(request.getId(), status, true);
    }

    private void settleBalance(LeaveRequest request, Employee owner, boolean approved, Instant now) {
        if ("UNPAID".equals(request.getLeaveType())) return;
        Map<Integer, Integer> daysByYear = new TreeMap<>();
        if (request.getStartDate().getYear() == request.getEndDate().getYear()) {
            daysByYear.put(request.getStartDate().getYear(), (int) request.getWorkingDays());
        } else {
            Set<LocalDate> holidayDates = holidays.datesBetween(request.getStartDate(), request.getEndDate());
            for (LocalDate date = request.getStartDate(); !date.isAfter(request.getEndDate()); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
                        && !holidayDates.contains(date)) daysByYear.merge(date.getYear(), 1, Integer::sum);
            }
            if (daysByYear.values().stream().mapToInt(Integer::intValue).sum() != request.getWorkingDays()) {
                throw error(LeaveErrorCode.CALENDAR_MISMATCH, "Request working days do not match the company calendar");
            }
        }
        for (var entry : daysByYear.entrySet()) {
            if (entry.getKey() < 1900 || entry.getKey() > 9999) {
                throw error(LeaveErrorCode.UNSUPPORTED_YEAR, "Unsupported balance year");
            }
            LeaveBalance balance = balances.findByEmployee_IdAndLeaveTypeAndBalanceYear(
                            owner.getId(), request.getLeaveType(), entry.getKey().shortValue())
                    .orElseThrow(() -> error(LeaveErrorCode.ALLOCATION_MISSING, "Leave allocation is missing for this request"));
            if (entry.getValue() <= 0 || balance.getReservedDays() < entry.getValue()) {
                throw error(LeaveErrorCode.RESERVATION_INSUFFICIENT, "Reserved leave does not cover this request");
            }
            balance.settleReservation(entry.getValue(), approved, now);
            balances.save(balance);
        }
    }

    private AppException error(AppErrorCode code, String message) {
        return new AppException(code, message);
    }

    public record DecisionResult(Long id, String status, boolean dashboardChanged) {}
}
