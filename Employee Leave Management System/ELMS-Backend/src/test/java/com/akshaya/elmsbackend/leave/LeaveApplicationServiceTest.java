package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.common.exception.AppException;
import com.akshaya.elmsbackend.employee.entity.Employee;
import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import com.akshaya.elmsbackend.leave.entity.LeaveApproval;
import com.akshaya.elmsbackend.leave.entity.LeaveBalance;
import com.akshaya.elmsbackend.leave.entity.LeaveRequest;
import com.akshaya.elmsbackend.leave.repository.CompanyHolidayRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveApprovalRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveBalanceRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LeaveApplicationServiceTest {

    private static final Clock COMPANY_CLOCK = Clock.fixed(
            Instant.parse("2026-09-23T00:00:00Z"),
            ZoneId.of("Asia/Kolkata"));

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
    private final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    private final LeaveApprovalRepository approvals = mock(LeaveApprovalRepository.class);
    private final CompanyHolidayRepository holidays = mock(CompanyHolidayRepository.class);
    private final LeaveApplicationService service = new LeaveApplicationService(
            employees,
            requests,
            balances,
            approvals,
            holidays,
            COMPANY_CLOCK);

    private Employee employee;
    private Employee manager;
    private Employee director;
    private LeaveBalance balance;

    @BeforeEach
    void setup() {
        director = new Employee();
        director.setId(1L);
        director.setEmployeeId("EMP001");
        director.setActive(true);

        manager = new Employee();
        manager.setId(2L);
        manager.setEmployeeId("EMP002");
        manager.setActive(true);
        manager.setManager(director);

        employee = new Employee();
        employee.setId(4L);
        employee.setEmployeeId("EMP004");
        employee.setActive(true);
        employee.setManager(manager);

        balance = BeanUtils.instantiateClass(LeaveBalance.class);
        set(balance, "entitledDays", (short) 10);
        set(balance, "carriedForwardDays", (short) 2);
        set(balance, "usedDays", (short) 3);
        set(balance, "reservedDays", (short) 1);

        when(employees.findForLeaveApplication("EMP004"))
                .thenReturn(Optional.of(employee));
        when(requests.countOverlappingActiveRequests(anyLong(), any(), any()))
                .thenReturn(0L);
        when(holidays.datesBetween(any(), any())).thenReturn(Set.of());
        when(balances.findByEmployee_IdAndLeaveTypeAndBalanceYear(
                4L,
                "ANNUAL",
                (short) 2030))
                .thenReturn(Optional.of(balance));
        when(balances.findByEmployee_IdAndLeaveTypeAndBalanceYear(
                4L,
                "ANNUAL",
                (short) 2026))
                .thenReturn(Optional.of(balance));
        when(balances.findByEmployee_IdAndLeaveTypeAndBalanceYear(
                4L,
                "CASUAL",
                (short) 2026))
                .thenReturn(Optional.of(balance));
        when(requests.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest request = invocation.getArgument(0);
            set(request, "id", 20L);
            return request;
        });
    }

    @Test
    void appliesLeaveReservesBalanceAndAssignsDirectManager() {
        var result = service.apply(
                "EMP004",
                " annual ",
                LocalDate.of(2030, 1, 7),
                LocalDate.of(2030, 1, 8),
                " Family event ");

        assertEquals(20L, result.id());
        assertEquals("PENDING", result.status());
        assertEquals("ANNUAL", result.leaveType());
        assertEquals(2, result.workingDays());
        assertEquals(3, balance.getReservedDays());
        verify(balances).save(balance);
        verify(approvals).save(any(LeaveApproval.class));
    }

    @Test
    void acceptsLeaveStartingOnTheCurrentCompanyDate() {
        var result = service.apply(
                "EMP004",
                "ANNUAL",
                LocalDate.of(2026, 9, 23),
                LocalDate.of(2026, 9, 24),
                null);

        assertEquals(LocalDate.of(2026, 9, 23), result.startDate());
        assertEquals(2, result.workingDays());
    }

    @Test
    void autoApprovesOneDayCasualLeaveAndRecordsTheAutoDecision() {
        var result = service.apply(
                "EMP004",
                "CASUAL",
                LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 9, 24),
                null);

        assertEquals("APPROVED", result.status());
        assertEquals(4, balance.getUsedDays());
        assertEquals(1, balance.getReservedDays());
        verify(approvals).save(argThat(approval ->
                "AUTO".equals(approval.getApprovalType())
                        && "APPROVED".equals(approval.getStatus())
                        && approval.getApprover() == null));
    }

    @Test
    void autoApprovesTwoWorkingDaysOfCasualLeave() {
        var result = service.apply(
                "EMP004",
                "CASUAL",
                LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 9, 25),
                null);

        assertEquals("APPROVED", result.status());
        assertEquals(2, result.workingDays());
        assertEquals(5, balance.getUsedDays());
    }

    @Test
    void rejectsCasualLeaveLongerThanTwoWorkingDays() {
        AppException exception = assertThrows(AppException.class, () -> service.apply(
                "EMP004",
                "CASUAL",
                LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 9, 28),
                null));

        assertEquals(LeaveErrorCode.CASUAL_DURATION_EXCEEDED, exception.getErrorCode());
        verify(requests, never()).save(any());
        verifyNoInteractions(balances);
        verifyNoInteractions(approvals);
    }

    @Test
    void longAnnualLeaveCreatesDirectAndSecondLevelStages() {
        var result = service.apply(
                "EMP004",
                "ANNUAL",
                LocalDate.of(2030, 1, 7),
                LocalDate.of(2030, 1, 14),
                null);

        assertEquals("PENDING", result.status());
        assertEquals(6, result.workingDays());

        ArgumentCaptor<LeaveApproval> stages = ArgumentCaptor.forClass(LeaveApproval.class);
        verify(approvals, times(2)).save(stages.capture());
        assertEquals("DIRECT_MANAGER", stages.getAllValues().get(0).getApprovalType());
        assertEquals(manager, stages.getAllValues().get(0).getApprover());
        assertEquals("SECOND_LEVEL_MANAGER", stages.getAllValues().get(1).getApprovalType());
        assertEquals(director, stages.getAllValues().get(1).getApprover());
    }

    @Test
    void longRequestRequiresAnActiveSecondLevelManager() {
        manager.setManager(null);

        AppException exception = assertThrows(AppException.class, () -> service.apply(
                "EMP004",
                "UNPAID",
                LocalDate.of(2030, 1, 7),
                LocalDate.of(2030, 1, 10),
                null));

        assertEquals(LeaveErrorCode.APPROVER_UNAVAILABLE, exception.getErrorCode());
        verify(requests, never()).save(any());
    }

    @Test
    void excludesWeekendsAndCompanyHolidays() {
        when(holidays.datesBetween(any(), any()))
                .thenReturn(Set.of(LocalDate.of(2030, 1, 9)));

        var result = service.apply(
                "EMP004",
                "ANNUAL",
                LocalDate.of(2030, 1, 7),
                LocalDate.of(2030, 1, 13),
                null);

        assertEquals(4, result.workingDays());
        assertEquals(5, balance.getReservedDays());
    }

    @Test
    void unpaidLeaveDoesNotReserveALeaveBalance() {
        var result = service.apply(
                "EMP004",
                "UNPAID",
                LocalDate.of(2030, 1, 7),
                LocalDate.of(2030, 1, 7),
                null);

        assertEquals(1, result.workingDays());
        verifyNoInteractions(balances);
        verify(approvals).save(any(LeaveApproval.class));
    }

    @Test
    void rejectsOverlappingActiveRequestBeforeChangingBalance() {
        when(requests.countOverlappingActiveRequests(anyLong(), any(), any()))
                .thenReturn(1L);

        AppException exception = assertThrows(AppException.class, () -> service.apply(
                "EMP004",
                "ANNUAL",
                LocalDate.of(2030, 1, 7),
                LocalDate.of(2030, 1, 8),
                null));

        assertEquals(LeaveErrorCode.OVERLAPPING_REQUEST, exception.getErrorCode());
        verifyNoInteractions(balances);
        verify(approvals, never()).save(any());
    }

    @Test
    void rejectsRequestWhenAvailableBalanceIsTooLow() {
        set(balance, "entitledDays", (short) 3);

        AppException exception = assertThrows(AppException.class, () -> service.apply(
                "EMP004",
                "ANNUAL",
                LocalDate.of(2030, 1, 7),
                LocalDate.of(2030, 1, 8),
                null));

        assertEquals(LeaveErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());
        verify(requests, never()).save(any());
        verify(approvals, never()).save(any());
    }

    @Test
    void rejectsInvalidTypeAndPastDates() {
        AppException invalidType = assertThrows(AppException.class, () -> service.apply(
                "EMP004",
                "BIRTHDAY",
                LocalDate.of(2030, 1, 7),
                LocalDate.of(2030, 1, 7),
                null));
        assertEquals(LeaveErrorCode.INVALID_LEAVE_TYPE, invalidType.getErrorCode());

        AppException pastDate = assertThrows(AppException.class, () -> service.apply(
                "EMP004",
                "ANNUAL",
                LocalDate.of(2020, 1, 7),
                LocalDate.of(2020, 1, 7),
                null));
        assertEquals(LeaveErrorCode.LEAVE_DATE_IN_PAST, pastDate.getErrorCode());
    }

    @Test
    void sickLeaveOfThreeWorkingDaysFlagsCertificateRequirement() {
        when(balances.findByEmployee_IdAndLeaveTypeAndBalanceYear(
                4L,
                "SICK",
                (short) 2030))
                .thenReturn(Optional.of(balance));

        var result = service.apply(
                "EMP004",
                "SICK",
                LocalDate.of(2030, 1, 7),
                LocalDate.of(2030, 1, 9),
                null);

        assertTrue(result.medicalCertificateRequired());
    }

    private void set(Object object, String field, Object value) {
        ReflectionTestUtils.setField(object, field, value);
    }
}
