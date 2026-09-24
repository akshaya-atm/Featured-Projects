package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.employee.entity.Employee;
import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import com.akshaya.elmsbackend.leave.entity.*;
import com.akshaya.elmsbackend.leave.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import com.akshaya.elmsbackend.common.exception.AppException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveDecisionServiceTest {
    private static final Clock COMPANY_CLOCK = Clock.fixed(
            Instant.parse("2026-09-23T00:00:00Z"),
            ZoneId.of("Asia/Kolkata"));
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
    private final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    private final LeaveApprovalRepository approvals = mock(LeaveApprovalRepository.class);
    private final CompanyHolidayRepository holidays = mock(CompanyHolidayRepository.class);
    private final LeaveDecisionService service = new LeaveDecisionService(
            employees, requests, balances, approvals, holidays, COMPANY_CLOCK);
    private Employee manager;
    private Employee owner;
    private LeaveRequest request;
    private LeaveBalance balance;

    @BeforeEach void setup() {
        manager = new Employee(); manager.setId(2L); manager.setEmployeeId("EMP002"); manager.setActive(true);
        owner = new Employee(); owner.setId(4L); owner.setEmployeeId("EMP004"); owner.setActive(true); owner.setManager(manager);
        request = BeanUtils.instantiateClass(LeaveRequest.class);
        set(request, "id", 10L); set(request, "employee", owner); set(request, "status", "PENDING");
        set(request, "leaveType", "ANNUAL"); set(request, "workingDays", (short) 2);
        set(request, "startDate", LocalDate.of(2026, 9, 21)); set(request, "endDate", LocalDate.of(2026, 9, 22));
        balance = balance(5);
        when(employees.findByEmployeeId("EMP002")).thenReturn(Optional.of(manager));
        when(employees.findForDecision(4L)).thenReturn(Optional.of(owner));
        when(requests.findForDecision(10L)).thenReturn(Optional.of(request));
        when(approvals.findByRequest_IdOrderBySequenceNo(10L))
                .thenReturn(List.of(new LeaveApproval(request, manager, Instant.now(COMPANY_CLOCK))));
        when(balances.findByEmployee_IdAndLeaveTypeAndBalanceYear(4L, "ANNUAL", (short) 2026)).thenReturn(Optional.of(balance));
    }

    private void set(Object object, String field, Object value) { ReflectionTestUtils.setField(object, field, value); }
    private LeaveBalance balance(int reserved) {
        LeaveBalance value = BeanUtils.instantiateClass(LeaveBalance.class);
        set(value, "entitledDays", (short) 18); set(value, "usedDays", (short) 3); set(value, "reservedDays", (short) reserved);
        return value;
    }
    private void assertFailure(int status) {
        assertEquals(status, assertThrows(AppException.class,
                () -> service.decide("EMP002", 10L, true, null)).getErrorCode().getStatus().value());
        verify(approvals, never()).save(any());
        verify(requests, never()).save(any());
    }

    @Test void approvalMovesReservedDaysToUsedAndRecordsDecision() {
        var result = service.decide("EMP002", 10L, true, "Approved");
        assertEquals("APPROVED", result.status()); assertTrue(result.dashboardChanged());
        assertEquals(5, balance.getUsedDays()); assertEquals(3, balance.getReservedDays());
        verify(approvals).save(argThat(a -> a.getApprover() == manager && a.getStatus().equals("APPROVED")));
    }
    @Test void rejectionReleasesReservedDaysWithoutIncreasingUsed() {
        assertEquals("REJECTED", service.decide("EMP002", 10L, false, "Coverage unavailable").status());
        assertEquals(3, balance.getUsedDays()); assertEquals(3, balance.getReservedDays());
    }
    @Test void duplicateDecisionCannotDebitTwice() {
        service.decide("EMP002", 10L, true, null);
        assertEquals(409, assertThrows(AppException.class,
                () -> service.decide("EMP002", 10L, true, null)).getErrorCode().getStatus().value());
        assertEquals(5, balance.getUsedDays()); verify(balances, times(1)).save(any());
    }
    @Test void unrelatedManagerIsDenied() {
        Employee other = new Employee(); other.setId(3L);
        when(approvals.findByRequest_IdOrderBySequenceNo(10L))
                .thenReturn(List.of(new LeaveApproval(request, other, Instant.now(COMPANY_CLOCK))));
        assertFailure(403);
        verifyNoInteractions(balances);
    }
    @Test void selfApprovalIsDeniedEvenWithMalformedHierarchy() {
        owner.setId(2L); owner.setManager(manager); when(employees.findForDecision(2L)).thenReturn(Optional.of(owner));
        assertFailure(403);
    }
    @Test void inactiveActorIsDenied() { manager.setActive(false); assertFailure(403); verifyNoInteractions(balances); }
    @Test void inactiveOwnerIsDenied() { owner.setActive(false); assertFailure(403); }
    @Test void previouslyRejectedRequestIsNotApproved() { set(request,"status","REJECTED"); assertFailure(409); }
    @Test void reservationMismatchDoesNotChangeRequest() { set(balance,"reservedDays",(short)1); assertFailure(409); assertEquals("PENDING",request.getStatus()); }
    @Test void missingAllocationReturnsConflict() {
        when(balances.findByEmployee_IdAndLeaveTypeAndBalanceYear(4L,"ANNUAL",(short)2026)).thenReturn(Optional.empty()); assertFailure(409);
    }
    @Test void unpaidRequestDoesNotTouchBalances() { set(request,"leaveType","UNPAID"); service.decide("EMP002",10L,true,null); verifyNoInteractions(balances); }
    @Test void longDecisionReasonIsRejected() {
        assertEquals(400,assertThrows(AppException.class,()->service.decide("EMP002",10L,false,"x".repeat(501))).getErrorCode().getStatus().value());
        verifyNoInteractions(balances);
    }
    @Test void laterStageKeepsRequestPendingAndReservationIntact() {
        Employee director = new Employee(); director.setId(1L); director.setEmployeeId("EMP001"); director.setActive(true);
        LeaveApproval direct = LeaveApproval.pending(
                request, manager, "DIRECT_MANAGER", 1, Instant.now(COMPANY_CLOCK));
        LeaveApproval later = LeaveApproval.pending(
                request, director, "SECOND_LEVEL_MANAGER", 2, Instant.now(COMPANY_CLOCK));
        when(approvals.findByRequest_IdOrderBySequenceNo(10L)).thenReturn(List.of(direct,later));
        assertEquals("PENDING",service.decide("EMP002",10L,true,null).status()); verifyNoInteractions(balances);
    }
    @Test void secondLevelManagerCompletesRequestAfterDirectManagerApproval() {
        Employee director = new Employee(); director.setId(1L); director.setEmployeeId("EMP001"); director.setActive(true);
        LeaveApproval direct = LeaveApproval.pending(
                request, manager, "DIRECT_MANAGER", 1, Instant.now(COMPANY_CLOCK));
        direct.decide("APPROVED", "Approved", Instant.now(COMPANY_CLOCK));
        LeaveApproval second = LeaveApproval.pending(
                request, director, "SECOND_LEVEL_MANAGER", 2, Instant.now(COMPANY_CLOCK));
        when(employees.findByEmployeeId("EMP001")).thenReturn(Optional.of(director));
        when(approvals.findByRequest_IdOrderBySequenceNo(10L)).thenReturn(List.of(direct, second));

        var result = service.decide("EMP001", 10L, true, "Approved");

        assertEquals("APPROVED", result.status());
        assertEquals(5, balance.getUsedDays());
        assertEquals(3, balance.getReservedDays());
        verify(approvals).save(argThat(a -> a == second && a.getStatus().equals("APPROVED")));
    }
    @Test void wrongApprovalStageCannotBeSkipped() {
        Employee director = new Employee(); director.setId(1L); director.setActive(true);
        LeaveApproval direct = LeaveApproval.pending(
                request, director, "DIRECT_MANAGER", 1, Instant.now(COMPANY_CLOCK));
        LeaveApproval second = LeaveApproval.pending(
                request, manager, "SECOND_LEVEL_MANAGER", 2, Instant.now(COMPANY_CLOCK));
        when(approvals.findByRequest_IdOrderBySequenceNo(10L)).thenReturn(List.of(direct, second));
        assertFailure(403);
    }
    @Test void crossYearRequestSettlesEachYearsWorkingDays() {
        set(request,"startDate",LocalDate.of(2026,12,31)); set(request,"endDate",LocalDate.of(2027,1,4));
        when(holidays.datesBetween(any(),any())).thenReturn(Set.of(LocalDate.of(2027,1,1)));
        LeaveBalance nextYear=balance(1);
        when(balances.findByEmployee_IdAndLeaveTypeAndBalanceYear(4L,"ANNUAL",(short)2027)).thenReturn(Optional.of(nextYear));
        service.decide("EMP002",10L,true,null);
        assertEquals(4,balance.getUsedDays()); assertEquals(4,nextYear.getUsedDays()); assertEquals(0,nextYear.getReservedDays());
    }
}
