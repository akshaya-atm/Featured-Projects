package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.employee.entity.Employee;
import com.akshaya.elmsbackend.employee.entity.Department;
import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import com.akshaya.elmsbackend.leave.entity.LeaveBalance;
import com.akshaya.elmsbackend.leave.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import com.akshaya.elmsbackend.common.exception.AppException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveReadServiceTest {
    private static final Clock COMPANY_CLOCK = Clock.fixed(
            Instant.parse("2026-09-23T00:00:00Z"),
            ZoneId.of("Asia/Kolkata"));
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    private final LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
    private final LeaveApprovalRepository approvals = mock(LeaveApprovalRepository.class);
    private final LeaveReadService service = new LeaveReadService(
            employees, balances, requests, approvals, COMPANY_CLOCK);

    private Employee employee() {
        Employee employee = new Employee();
        employee.setId(4L);
        employee.setEmployeeId("EMP004");
        employee.setFullName("Arun Kumar");
        employee.setActive(true);
        Department department = BeanUtils.instantiateClass(Department.class);
        ReflectionTestUtils.setField(department, "name", "Engineering");
        employee.setDepartment(department);
        when(employees.findByEmployeeId("EMP004")).thenReturn(Optional.of(employee));
        return employee;
    }

    @Test void calculatesCarryForwardUsedAndReservedAndDoesNotInventMissingAllocations() {
        employee();
        LeaveBalance balance = BeanUtils.instantiateClass(LeaveBalance.class);
        ReflectionTestUtils.setField(balance, "leaveType", "ANNUAL");
        ReflectionTestUtils.setField(balance, "entitledDays", (short) 18);
        ReflectionTestUtils.setField(balance, "carriedForwardDays", (short) 2);
        ReflectionTestUtils.setField(balance, "usedDays", (short) 3);
        ReflectionTestUtils.setField(balance, "reservedDays", (short) 4);
        when(balances.findByEmployee_IdAndBalanceYear(4L, (short) 2026)).thenReturn(List.of(balance));
        var result = service.getBalances("EMP004", 2026);
        assertEquals(20, result.annual().total());
        assertEquals(13, result.annual().remaining());
        assertNull(result.sick());
        assertNull(result.casual());
    }

    @Test void usesCurrentCompanyYearByDefault() {
        employee();
        short year = 2026;
        when(balances.findByEmployee_IdAndBalanceYear(4L, year)).thenReturn(List.of());
        assertEquals(year, service.getBalances("EMP004", null).year());
    }

    @Test void rejectsInvalidYearBeforeBalanceQuery() {
        employee();
        assertEquals(400, assertThrows(AppException.class,
                () -> service.getBalances("EMP004", 100000)).getErrorCode().getStatus().value());
        verifyNoInteractions(balances);
    }

    @Test void deniesTeamAccessWithoutDirectReports() {
        employee();
        assertEquals(403, assertThrows(AppException.class,
                () -> service.getPendingTeamRequests("EMP004")).getErrorCode().getStatus().value());
        verifyNoInteractions(requests);
    }

    @Test void scopesTeamLookupToAuthenticatedEmployeesInternalId() {
        employee();
        when(employees.existsByManager_IdAndActiveTrueAndIdNot(4L, 4L)).thenReturn(true);
        when(approvals.findActionableForApprover(4L)).thenReturn(List.of());
        assertTrue(service.getPendingTeamRequests("EMP004").requests().isEmpty());
        verify(approvals).findActionableForApprover(4L);
    }

    @Test void ownRequestsAreScopedAndIncludeAllStatuses() {
        employee();
        when(requests.findByEmployee_IdOrderByCreatedAtDescIdDesc(4L)).thenReturn(List.of());
        assertTrue(service.getOwnRequests("EMP004").requests().isEmpty());
        verify(requests).findByEmployee_IdOrderByCreatedAtDescIdDesc(4L);
    }

    @Test void inactiveEmployeeCannotReadBalances() {
        employee().setActive(false);
        assertEquals(403, assertThrows(AppException.class,
                () -> service.getBalances("EMP004", 2026)).getErrorCode().getStatus().value());
        verifyNoInteractions(balances);
    }

    @Test void missingEmployeeReturns404() {
        assertEquals(404, assertThrows(AppException.class,
                () -> service.getBalances("missing", 2026)).getErrorCode().getStatus().value());
    }

}
