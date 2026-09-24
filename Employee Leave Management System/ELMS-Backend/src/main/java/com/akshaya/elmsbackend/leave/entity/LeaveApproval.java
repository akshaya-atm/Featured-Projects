package com.akshaya.elmsbackend.leave.entity;

import com.akshaya.elmsbackend.employee.entity.Employee;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "leave_approvals")
public class LeaveApproval {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false)
    private LeaveRequest request;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_ref_id")
    private Employee approver;
    @Column(name = "approval_type", nullable = false, length = 30)
    private String approvalType;
    @Column(name = "sequence_no", nullable = false)
    private short sequenceNo;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "decision_reason", length = 500)
    private String decisionReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "acted_at")
    private Instant actedAt;

    protected LeaveApproval() {}

    public LeaveApproval(LeaveRequest request, Employee approver, Instant now) {
        this(request, approver, "DIRECT_MANAGER", (short) 1, "PENDING", now, null);
    }

    private LeaveApproval(
            LeaveRequest request,
            Employee approver,
            String approvalType,
            short sequenceNo,
            String status,
            Instant createdAt,
            Instant actedAt) {
        this.request = request;
        this.approver = approver;
        this.approvalType = approvalType;
        this.sequenceNo = sequenceNo;
        this.status = status;
        this.createdAt = createdAt;
        this.actedAt = actedAt;
    }

    public static LeaveApproval pending(
            LeaveRequest request,
            Employee approver,
            String approvalType,
            int sequenceNo,
            Instant now) {

        if (approver == null || sequenceNo <= 0) {
            throw new IllegalArgumentException("A pending approval requires an approver and sequence number");
        }
        return new LeaveApproval(
                request,
                approver,
                approvalType,
                (short) sequenceNo,
                "PENDING",
                now,
                null);
    }

    public static LeaveApproval autoApproved(LeaveRequest request, Instant now) {
        return new LeaveApproval(
                request,
                null,
                "AUTO",
                (short) 1,
                "APPROVED",
                now,
                now);
    }

    public LeaveRequest getRequest() { return request; }
    public Employee getApprover() { return approver; }
    public String getApprovalType() { return approvalType; }
    public short getSequenceNo() { return sequenceNo; }
    public String getStatus() { return status; }
    public void decide(String status, String reason, Instant now) {
        this.status = status;
        this.decisionReason = reason;
        this.actedAt = now;
    }
}
