package com.akshaya.elmsbackend.employee.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "employee_credentials")
public class EmployeeCredential {

    @Id
    @Column(name = "employee_ref_id")
    private Long employeeRefId;
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "employee_ref_id")
    private Employee employee;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
    public Long getEmployeeRefId() {
        return employeeRefId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }
    public void setEmployeeRefId(Long employeeRefId) {
        this.employeeRefId = employeeRefId;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}