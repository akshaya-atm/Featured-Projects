package com.akshaya.elmsbackend.leave;

import com.akshaya.elmsbackend.common.exception.AppException;
import com.akshaya.elmsbackend.employee.EmployeeErrorCode;
import com.akshaya.elmsbackend.employee.entity.Employee;
import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import com.akshaya.elmsbackend.leave.entity.LeaveAttachment;
import com.akshaya.elmsbackend.leave.entity.LeaveRequest;
import com.akshaya.elmsbackend.leave.repository.LeaveAttachmentRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveApprovalRepository;
import com.akshaya.elmsbackend.leave.repository.LeaveRequestRepository;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

@Service
@Transactional
public class MedicalCertificateService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveAttachmentRepository leaveAttachmentRepository;
    private final LeaveApprovalRepository leaveApprovalRepository;
    private final EmployeeRepository employeeRepository;

    private static final long MAX_FILE_SIZE = 1 * 1024 * 1024; // 1 MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "application/pdf", "image/png", "image/jpeg", "image/jpg"
    );

    public MedicalCertificateService(LeaveRequestRepository leaveRequestRepository,
                                     LeaveAttachmentRepository leaveAttachmentRepository,
                                     LeaveApprovalRepository leaveApprovalRepository,
                                     EmployeeRepository employeeRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveAttachmentRepository = leaveAttachmentRepository;
        this.leaveApprovalRepository = leaveApprovalRepository;
        this.employeeRepository = employeeRepository;
    }

    public void uploadCertificate(String employeeId, Long requestId, MultipartFile file) {
        LeaveRequest request = requireUploadEligibleRequest(employeeId, requestId);

        if (file.isEmpty()) {
            throw new AppException(LeaveErrorCode.INVALID_FILE_TYPE, "File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException(LeaveErrorCode.FILE_TOO_LARGE, "File must be smaller than 1MB");
        }

        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType().toLowerCase())) {
            throw new AppException(LeaveErrorCode.INVALID_FILE_TYPE, "File must be a PDF, PNG, or JPEG");
        }

        try {
            // Delete old attachment if it exists
            leaveAttachmentRepository.findByLeaveRequest_Id(request.getId())
                    .ifPresent(leaveAttachmentRepository::delete);

            LeaveAttachment attachment = new LeaveAttachment(
                    request,
                    "MEDICAL_CERTIFICATE",
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "certificate",
                    file.getContentType(),
                    file.getBytes()
            );

            leaveAttachmentRepository.save(attachment);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    @Transactional(readOnly = true)
    public UploadPreparation prepareUpload(String employeeId, Long requestId) {
        LeaveRequest request = requireUploadEligibleRequest(employeeId, requestId);
        return new UploadPreparation(
                request.getId(),
                leaveAttachmentRepository.existsByLeaveRequest_Id(request.getId()));
    }

    public Optional<Long> claimNextReminder(String employeeId) {
        Employee employee = employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new AppException(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND,
                        "Employee details not found"));
        if (!employee.isActive()) {
            throw new AppException(
                    EmployeeErrorCode.EMPLOYEE_INACTIVE,
                    "Employee account is inactive");
        }

        return leaveRequestRepository
                .findCertificateReminderCandidates(
                        employee.getId(),
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(request -> {
                    request.markCertificateReminderShown(Instant.now());
                    leaveRequestRepository.save(request);
                    return request.getId();
                });
    }

    public void recordReminderShown(String employeeId, Long requestId) {
        Employee employee = employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new AppException(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND,
                        "Employee details not found"));
        if (!employee.isActive()) {
            throw new AppException(
                    EmployeeErrorCode.EMPLOYEE_INACTIVE,
                    "Employee account is inactive");
        }

        LeaveRequest request = leaveRequestRepository
                .findForCertificateReminder(requestId)
                .orElseThrow(() -> new AppException(
                        LeaveErrorCode.REQUEST_NOT_FOUND,
                        "Leave request not found"));
        if (!request.getEmployee().getId().equals(employee.getId())) {
            throw new AppException(
                    LeaveErrorCode.NOT_YOUR_TEAM,
                    "You can only manage reminders for your own requests");
        }

        request.markCertificateReminderShown(Instant.now());
        leaveRequestRepository.save(request);
    }

    private LeaveRequest requireUploadEligibleRequest(String employeeId, Long requestId) {
        Employee employee = employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new AppException(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "Employee details not found"));
        if (!employee.isActive()) {
            throw new AppException(EmployeeErrorCode.EMPLOYEE_INACTIVE, "Employee account is inactive");
        }

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(LeaveErrorCode.REQUEST_NOT_FOUND, "Leave request not found"));

        if (!request.getEmployee().getId().equals(employee.getId())) {
            throw new AppException(LeaveErrorCode.NOT_YOUR_TEAM, "You can only upload certificates for your own requests");
        }

        if (!"PENDING".equals(request.getStatus()) && !"APPROVED".equals(request.getStatus())) {
            throw new AppException(LeaveErrorCode.UPLOAD_NOT_ALLOWED, "Cannot upload a certificate for a request that is not PENDING or APPROVED");
        }
        
        if (!"SICK".equals(request.getLeaveType())) {
            throw new AppException(LeaveErrorCode.UPLOAD_NOT_ALLOWED, "Medical certificates are only for SICK leave");
        }
        return request;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadCertificate(String employeeId, Long requestId) {
        Employee employee = employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new AppException(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "Employee details not found"));
        if (!employee.isActive()) {
            throw new AppException(EmployeeErrorCode.EMPLOYEE_INACTIVE, "Employee account is inactive");
        }

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(LeaveErrorCode.REQUEST_NOT_FOUND, "Leave request not found"));

        boolean isOwner = request.getEmployee().getId().equals(employee.getId());
        boolean assignedApprover = !isOwner && leaveApprovalRepository
                .existsByRequest_IdAndApprover_Id(requestId, employee.getId());
        if (!isOwner && !assignedApprover) {
            throw new AppException(LeaveErrorCode.NOT_YOUR_TEAM, "You don't have permission to view this certificate");
        }

        LeaveAttachment attachment = leaveAttachmentRepository.findByLeaveRequest_Id(request.getId())
                .orElseThrow(() -> new AppException(LeaveErrorCode.CERTIFICATE_NOT_FOUND, "No certificate uploaded for this request"));

        ByteArrayResource resource = new ByteArrayResource(attachment.getFileData());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getFileData().length)
                .body(resource);
    }

    public record UploadPreparation(
            Long requestId,
            boolean replacingExistingCertificate) {
    }
}
