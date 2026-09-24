package com.akshaya.elmsbackend.leave.entity;

import com.akshaya.elmsbackend.employee.entity.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "leave_requests")
public class LeaveRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_ref_id", nullable = false)
    private Employee employee;

    @Column(name = "leave_type", nullable = false, length = 20)
    private String leaveType;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Column(name = "working_days", nullable = false)
    private short workingDays;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 500)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "certificate_reminder_shown_at")
    private Instant certificateReminderShownAt;

    @OneToOne(mappedBy = "leaveRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private LeaveAttachment attachment;

    protected LeaveRequest() {}

    public LeaveRequest(
            Employee employee,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            int workingDays,
            String reason,
            Instant now) {

        if (workingDays <= 0 || workingDays > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Working days are outside the supported range");
        }
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.workingDays = (short) workingDays;
        this.status = "PENDING";
        this.reason = reason;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public String getLeaveType() { return leaveType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public short getWorkingDays() { return workingDays; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public LeaveAttachment getAttachment() { return attachment; }
    public Instant getCertificateReminderShownAt() { return certificateReminderShownAt; }
    public void autoApprove(Instant now) {
        if (!"PENDING".equals(status)) {
            throw new IllegalStateException("Only a pending request can be auto-approved");
        }
        this.status = "APPROVED";
        this.updatedAt = now;
    }
    public void decide(String status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }
    public void markCertificateReminderShown(Instant now) {
        if (certificateReminderShownAt == null) {
            certificateReminderShownAt = now;
            updatedAt = now;
        }
    }
}
