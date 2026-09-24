package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.common.exception.AppException;
import com.akshaya.elmsbackend.employee.entity.Employee;
import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import com.akshaya.elmsbackend.leave.entity.LeaveRequest;
import com.akshaya.elmsbackend.leave.entity.LeaveAttachment;
import com.akshaya.elmsbackend.leave.repository.LeaveApprovalRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveAttachmentRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MedicalCertificateServiceTest {

    private final LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
    private final LeaveAttachmentRepository attachments = mock(LeaveAttachmentRepository.class);
    private final LeaveApprovalRepository approvals = mock(LeaveApprovalRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final MedicalCertificateService service = new MedicalCertificateService(
            requests,
            attachments,
            approvals,
            employees);

    private Employee employee;
    private LeaveRequest request;

    @BeforeEach
    void setup() {
        employee = new Employee();
        employee.setId(4L);
        employee.setEmployeeId("EMP004");
        employee.setActive(true);

        request = BeanUtils.instantiateClass(LeaveRequest.class);
        set(request, "id", 20L);
        set(request, "employee", employee);
        set(request, "leaveType", "SICK");
        set(request, "status", "PENDING");

        when(employees.findByEmployeeId("EMP004")).thenReturn(Optional.of(employee));
        when(requests.findById(20L)).thenReturn(Optional.of(request));
    }

    @Test
    void preparesUploadOnlyAfterCheckingTheAuthenticatedOwner() {
        when(attachments.existsByLeaveRequest_Id(20L)).thenReturn(true);

        var result = service.prepareUpload("EMP004", 20L);

        assertEquals(20L, result.requestId());
        assertTrue(result.replacingExistingCertificate());
    }

    @Test
    void rejectsUploadPreparationForAnotherEmployeesRequest() {
        Employee otherEmployee = new Employee();
        otherEmployee.setId(8L);
        otherEmployee.setEmployeeId("EMP008");
        otherEmployee.setActive(true);
        when(employees.findByEmployeeId("EMP008")).thenReturn(Optional.of(otherEmployee));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.prepareUpload("EMP008", 20L));

        assertEquals(LeaveErrorCode.NOT_YOUR_TEAM, exception.getErrorCode());
    }

    @Test
    void rejectsUploadPreparationForNonSickLeave() {
        set(request, "leaveType", "ANNUAL");

        AppException exception = assertThrows(
                AppException.class,
                () -> service.prepareUpload("EMP004", 20L));

        assertEquals(LeaveErrorCode.UPLOAD_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    void rejectsUploadPreparationForCompletedRequest() {
        set(request, "status", "COMPLETED");

        AppException exception = assertThrows(
                AppException.class,
                () -> service.prepareUpload("EMP004", 20L));

        assertEquals(LeaveErrorCode.UPLOAD_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    void claimsAndPersistsTheFirstUnshownReminder() {
        when(requests.findCertificateReminderCandidates(
                eq(4L),
                any(Pageable.class)))
                .thenReturn(List.of(request));

        Optional<Long> result = service.claimNextReminder("EMP004");

        assertEquals(Optional.of(20L), result);
        assertNotNull(request.getCertificateReminderShownAt());
        verify(requests).save(request);
    }

    @Test
    void doesNothingWhenNoUnshownReminderExists() {
        when(requests.findCertificateReminderCandidates(
                eq(4L),
                any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(service.claimNextReminder("EMP004").isEmpty());
        verify(requests, never()).save(any());
    }

    @Test
    void recordsThatAToolGeneratedUploadPromptWasShown() {
        when(requests.findForCertificateReminder(20L))
                .thenReturn(Optional.of(request));

        service.recordReminderShown("EMP004", 20L);

        assertNotNull(request.getCertificateReminderShownAt());
        verify(requests).save(request);
    }

    @Test
    void ownerCanDownloadCertificate() throws Exception {
        byte[] data = {1, 2, 3};
        when(attachments.findByLeaveRequest_Id(20L)).thenReturn(Optional.of(
                new LeaveAttachment(request, "MEDICAL_CERTIFICATE", "note.pdf", "application/pdf", data)));

        var response = service.downloadCertificate("EMP004", 20L);

        assertArrayEquals(data, response.getBody().getInputStream().readAllBytes());
        verifyNoInteractions(approvals);
    }

    @Test
    void secondLevelApproverCanDownloadCertificate() throws Exception {
        Employee director = new Employee();
        director.setId(1L);
        director.setEmployeeId("EMP001");
        director.setActive(true);
        when(employees.findByEmployeeId("EMP001")).thenReturn(Optional.of(director));
        when(approvals.existsByRequest_IdAndApprover_Id(20L, 1L)).thenReturn(true);
        when(attachments.findByLeaveRequest_Id(20L)).thenReturn(Optional.of(
                new LeaveAttachment(request, "MEDICAL_CERTIFICATE", "note.pdf", "application/pdf", new byte[]{7})));

        var response = service.downloadCertificate("EMP001", 20L);

        assertArrayEquals(new byte[]{7}, response.getBody().getInputStream().readAllBytes());
    }

    @Test
    void unrelatedEmployeeCannotDownloadCertificate() {
        Employee unrelated = new Employee();
        unrelated.setId(8L);
        unrelated.setEmployeeId("EMP008");
        unrelated.setActive(true);
        when(employees.findByEmployeeId("EMP008")).thenReturn(Optional.of(unrelated));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.downloadCertificate("EMP008", 20L));

        assertEquals(LeaveErrorCode.NOT_YOUR_TEAM, exception.getErrorCode());
        verifyNoInteractions(attachments);
    }

    @Test
    void inactiveApproverCannotDownloadCertificate() {
        Employee director = new Employee();
        director.setId(1L);
        director.setEmployeeId("EMP001");
        director.setActive(false);
        when(employees.findByEmployeeId("EMP001")).thenReturn(Optional.of(director));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.downloadCertificate("EMP001", 20L));

        assertEquals(com.akshaya.elmsbackend.employee.EmployeeErrorCode.EMPLOYEE_INACTIVE,
                exception.getErrorCode());
        verifyNoInteractions(approvals, attachments);
    }

    private void set(Object object, String field, Object value) {
        ReflectionTestUtils.setField(object, field, value);
    }
}
