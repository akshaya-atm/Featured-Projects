package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.employee.entity.Employee;
import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import com.akshaya.elmsbackend.leave.dto.LeaveResponses.*;
import com.akshaya.elmsbackend.leave.entity.LeaveBalance;
import com.akshaya.elmsbackend.leave.entity.LeaveRequest;
import com.akshaya.elmsbackend.leave.repository.LeaveBalanceRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveApprovalRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.akshaya.elmsbackend.common.exception.AppException;
import com.akshaya.elmsbackend.employee.EmployeeErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class LeaveReadService {
    private final EmployeeRepository employees;
    private final LeaveBalanceRepository balances;
    private final LeaveRequestRepository requests;
    private final LeaveApprovalRepository approvals;
    private final Clock companyClock;

    public LeaveReadService(EmployeeRepository employees, LeaveBalanceRepository balances,
                            LeaveRequestRepository requests,
                            LeaveApprovalRepository approvals,
                            Clock companyClock) {
        this.employees = employees;
        this.balances = balances;
        this.requests = requests;
        this.approvals = approvals;
        this.companyClock = companyClock;
    }


    public Balances getBalances(String employeeId, Integer year) {
        Employee employee = currentEmployee(employeeId);
        int selectedYear = year == null ? Year.now(companyClock).getValue() : year;
        if (selectedYear < 1900 || selectedYear > 9999) {
            throw new AppException(LeaveErrorCode.INVALID_YEAR, "Year must be between 1900 and 9999");
        }
        return readBalances(employee, selectedYear);
    }

    public Requests getOwnRequests(String employeeId) {
        Employee employee = currentEmployee(employeeId);
        return new Requests(requests.findByEmployee_IdOrderByCreatedAtDescIdDesc(employee.getId())
                .stream().map(this::toResponse).toList());
    }

    public Requests getPendingTeamRequests(String employeeId) {
        Employee employee = currentEmployee(employeeId);
        if (!hasDirectReports(employee)) {
            throw new AppException(LeaveErrorCode.NO_DIRECT_REPORTS, "No direct reports available for review");
        }
        return new Requests(approvals.findActionableForApprover(employee.getId())
                .stream()
                .map(approval -> toResponse(approval.getRequest()))
                .toList());
    }

    private Employee currentEmployee(String employeeId) {
        Employee employee = employees.findByEmployeeId(employeeId)
                .orElseThrow(() -> new AppException(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "Employee details not found"));
        if (!employee.isActive()) {
            throw new AppException(EmployeeErrorCode.EMPLOYEE_INACTIVE, "Employee account is inactive");
        }
        return employee;
    }

    private boolean hasDirectReports(Employee employee) {
        return employees.existsByManager_IdAndActiveTrueAndIdNot(employee.getId(), employee.getId());
    }

    private Balances readBalances(Employee employee, int year) {
        Map<String, Balance> result = new HashMap<>();
        for (LeaveBalance balance : balances.findByEmployee_IdAndBalanceYear(employee.getId(), (short) year)) {
            int total = balance.getEntitledDays() + balance.getCarriedForwardDays();
            int used = balance.getUsedDays();
            int reserved = balance.getReservedDays();
            result.put(balance.getLeaveType(), new Balance(total, used, reserved, total - used - reserved));
        }
        // Missing allocations remain null, rather than being presented as zero entitlement.
        return new Balances(year, result.get("ANNUAL"), result.get("CASUAL"), result.get("SICK"));
    }

    private Request toResponse(LeaveRequest request) {
        Employee employee = request.getEmployee();
        
        boolean required = "SICK".equals(request.getLeaveType()) && request.getWorkingDays() >= 3;
        boolean uploaded = request.getAttachment() != null;
        String fileName = uploaded ? request.getAttachment().getFileName() : null;
        boolean overdue = required && !uploaded && LocalDate.now(companyClock).isAfter(request.getEndDate());
        
        MedicalCertificateDto certDto = new MedicalCertificateDto(required, uploaded, fileName, overdue);

        return new Request(request.getId(), employee.getEmployeeId(), employee.getFullName(),
                request.getLeaveType(), request.getStartDate(), request.getEndDate(),
                request.getWorkingDays(), request.getStatus(), request.getReason(), certDto);
    }
}
