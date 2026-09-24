package com.akshaya.elmsbackend.employee.repository;

import com.akshaya.elmsbackend.employee.entity.EmployeeCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeCredentialRepository
        extends JpaRepository<EmployeeCredential, Long> {
}