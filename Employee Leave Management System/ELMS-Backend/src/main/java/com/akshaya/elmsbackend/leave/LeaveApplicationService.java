package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.common.exception.AppException;
import com.akshaya.elmsbackend.common.exception.AppErrorCode;
import com.akshaya.elmsbackend.employee.EmployeeErrorCode;
import com.akshaya.elmsbackend.employee.entity.Employee;
import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import com.akshaya.elmsbackend.leave.entity.LeaveApproval;
import com.akshaya.elmsbackend.leave.entity.LeaveBalance;
import com.akshaya.elmsbackend.leave.entity.LeaveRequest;
import com.akshaya.elmsbackend.leave.repository.CompanyHolidayRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveApprovalRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveBalanceRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class LeaveApplicationService {

    private static final Set<String> SUPPORTED_TYPES =
            Set.of("ANNUAL", "CASUAL", "SICK", "UNPAID");

    private final EmployeeRepository employees;
    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final LeaveApprovalRepository approvals;
    private final CompanyHolidayRepository holidays;
    private final Clock companyClock;

    public LeaveApplicationService(
            EmployeeRepository employees,
            LeaveRequestRepository requests,
            LeaveBalanceRepository balances,
            LeaveApprovalRepository approvals,
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
    public ApplicationResult apply(
            String employeeId,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            String reason) {

        String normalizedType = normalizeType(leaveType);
        validateDates(startDate, endDate);
        String normalizedReason = normalizeReason(reason);

        Employee employee = employees.findForLeaveApplication(employeeId)
                .orElseThrow(() -> error(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND,
                        "Employee details not found"));
        if (!employee.isActive()) {
            throw error(EmployeeErrorCode.EMPLOYEE_INACTIVE, "Employee account is inactive");
        }
        if (requests.countOverlappingActiveRequests(employee.getId(), startDate, endDate) > 0) {
            throw error(
                    LeaveErrorCode.OVERLAPPING_REQUEST,
                    "An active leave request already overlaps these dates");
        }

        Set<LocalDate> holidayDates = holidays.datesBetween(startDate, endDate);
        Map<Integer, Integer> workingDaysByYear = workingDaysByYear(
                startDate,
                endDate,
                holidayDates);
        int workingDays = workingDaysByYear.values().stream().mapToInt(Integer::intValue).sum();
        if (workingDays == 0) {
            throw error(
                    LeaveErrorCode.NO_WORKING_DAYS,
                    "The selected dates contain no company working days");
        }
        if (workingDays > Short.MAX_VALUE) {
            throw error(
                    LeaveErrorCode.INVALID_LEAVE_DATES,
                    "The selected date range is too large");
        }

        ApprovalRoute route = approvalRoute(normalizedType, workingDays);
        var approvalStages = approvalStages(employee, route);
        Instant now = Instant.now(companyClock);
        if (!"UNPAID".equals(normalizedType)) {
            reserveBalance(
                    employee,
                    normalizedType,
                    workingDaysByYear,
                    route == ApprovalRoute.AUTO,
                    now);
        }

        LeaveRequest request = new LeaveRequest(
                employee,
                normalizedType,
                startDate,
                endDate,
                workingDays,
                normalizedReason,
                now);

        if (route == ApprovalRoute.AUTO) {
            request.autoApprove(now);
        }
        request = requests.save(request);

        if (route == ApprovalRoute.AUTO) {
            approvals.save(LeaveApproval.autoApproved(request, now));
        } else {
            for (int index = 0; index < approvalStages.size(); index++) {
                ApprovalStage stage = approvalStages.get(index);
                approvals.save(LeaveApproval.pending(
                        request,
                        stage.approver(),
                        stage.approvalType(),
                        index + 1,
                        now));
            }
        }

        return new ApplicationResult(
                request.getId(),
                request.getStatus(),
                normalizedType,
                startDate,
                endDate,
                workingDays,
                "SICK".equals(normalizedType) && workingDays >= 3);
    }

    private String normalizeType(String leaveType) {
        String normalized = leaveType == null
                ? ""
                : leaveType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw error(
                    LeaveErrorCode.INVALID_LEAVE_TYPE,
                    "Leave type must be ANNUAL, CASUAL, SICK, or UNPAID");
        }
        return normalized;
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw error(
                    LeaveErrorCode.INVALID_LEAVE_DATES,
                    "End date must be on or after start date");
        }
        if (startDate.isBefore(LocalDate.now(companyClock))) {
            throw error(
                    LeaveErrorCode.LEAVE_DATE_IN_PAST,
                    "Leave cannot start in the past");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw error(
                    LeaveErrorCode.INVALID_REASON_LENGTH,
                    "Leave reason must be at most 500 characters");
        }
        return normalized;
    }

    private ApprovalRoute approvalRoute(String leaveType, int workingDays) {
        if ("CASUAL".equals(leaveType)) {
            if (workingDays > 2) {
                throw error(
                        LeaveErrorCode.CASUAL_DURATION_EXCEEDED,
                        "Casual Leave is limited to 2 working days per request");
            }
            return ApprovalRoute.AUTO;
        }
        if (("ANNUAL".equals(leaveType) && workingDays > 5)
                || ("UNPAID".equals(leaveType) && workingDays > 3)) {
            return ApprovalRoute.DIRECT_AND_SECOND_LEVEL;
        }
        return ApprovalRoute.DIRECT_MANAGER;
    }

    private java.util.List<ApprovalStage> approvalStages(
            Employee employee,
            ApprovalRoute route) {

        if (route == ApprovalRoute.AUTO) {
            return java.util.List.of();
        }

        Employee directManager = employee.getManager();
        if (directManager == null || !directManager.isActive()) {
            throw error(
                    LeaveErrorCode.APPROVER_UNAVAILABLE,
                    "An active direct manager must be assigned before leave can be submitted");
        }

        if (route == ApprovalRoute.DIRECT_MANAGER) {
            return java.util.List.of(new ApprovalStage(directManager, "DIRECT_MANAGER"));
        }

        Employee secondLevelManager = directManager.getManager();
        if (secondLevelManager == null
                || !secondLevelManager.isActive()
                || secondLevelManager.getId().equals(employee.getId())) {
            throw error(
                    LeaveErrorCode.APPROVER_UNAVAILABLE,
                    "An active second-level manager must be assigned for this request");
        }
        return java.util.List.of(
                new ApprovalStage(directManager, "DIRECT_MANAGER"),
                new ApprovalStage(secondLevelManager, "SECOND_LEVEL_MANAGER"));
    }

    private Map<Integer, Integer> workingDaysByYear(
            LocalDate startDate,
            LocalDate endDate,
            Set<LocalDate> holidayDates) {

        Map<Integer, Integer> result = new TreeMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY
                    && !holidayDates.contains(date)) {
                result.merge(date.getYear(), 1, Integer::sum);
            }
        }
        return result;
    }

    private void reserveBalance(
            Employee employee,
            String leaveType,
            Map<Integer, Integer> workingDaysByYear,
            boolean approveImmediately,
            Instant now) {

        for (Map.Entry<Integer, Integer> entry : workingDaysByYear.entrySet()) {
            int year = entry.getKey();
            if (year < 1900 || year > 9999) {
                throw error(
                        LeaveErrorCode.UNSUPPORTED_YEAR,
                        "Unsupported balance year");
            }
            LeaveBalance balance = balances
                    .findByEmployee_IdAndLeaveTypeAndBalanceYear(
                            employee.getId(),
                            leaveType,
                            (short) year)
                    .orElseThrow(() -> error(
                            LeaveErrorCode.ALLOCATION_MISSING,
                            "Leave allocation is missing for " + year));
            int available = balance.getEntitledDays()
                    + balance.getCarriedForwardDays()
                    - balance.getUsedDays()
                    - balance.getReservedDays();
            if (available < entry.getValue()) {
                throw error(
                        LeaveErrorCode.INSUFFICIENT_BALANCE,
                        "Insufficient " + leaveType + " leave balance for " + year);
            }
            balance.reserve(entry.getValue(), now);
            if (approveImmediately) {
                balance.settleReservation(entry.getValue(), true, now);
            }
            balances.save(balance);
        }
    }

    private AppException error(AppErrorCode code, String message) {
        return new AppException(code, message);
    }

    public record ApplicationResult(
            Long id,
            String status,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            int workingDays,
            boolean medicalCertificateRequired) {
    }

    private enum ApprovalRoute {
        AUTO,
        DIRECT_MANAGER,
        DIRECT_AND_SECOND_LEVEL
    }

    private record ApprovalStage(Employee approver, String approvalType) {
    }
}
