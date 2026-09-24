package com.akshaya.elmsbackend.leave.entity;

import com.akshaya.elmsbackend.employee.entity.Employee;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "leave_balances")
public class LeaveBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_ref_id", nullable = false)
    private Employee employee;

    @Column(name = "leave_type", nullable = false, length = 20)
    private String leaveType;
    @Column(name = "balance_year", nullable = false)
    private short balanceYear;
    @Column(name = "entitled_days", nullable = false)
    private short entitledDays;
    @Column(name = "carried_forward_days", nullable = false)
    private short carriedForwardDays;
    @Column(name = "used_days", nullable = false)
    private short usedDays;
    @Column(name = "reserved_days", nullable = false)
    private short reservedDays;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LeaveBalance() {}

    public String getLeaveType() { return leaveType; }
    public short getEntitledDays() { return entitledDays; }
    public short getCarriedForwardDays() { return carriedForwardDays; }
    public short getUsedDays() { return usedDays; }
    public short getReservedDays() { return reservedDays; }

    public void reserve(int days, Instant now) {
        int total = entitledDays + carriedForwardDays;
        if (days <= 0 || usedDays + reservedDays + days > total) {
            throw new IllegalStateException("Insufficient available leave balance");
        }
        reservedDays = (short) (reservedDays + days);
        updatedAt = now;
    }

    public void settleReservation(int days, boolean approved, Instant now) {
        if (days <= 0 || reservedDays < days) {
            throw new IllegalStateException("Insufficient reserved leave for this request");
        }
        reservedDays = (short) (reservedDays - days);
        if (approved) usedDays = (short) (usedDays + days);
        updatedAt = now;
    }
}
