package com.akshaya.elmsbackend.employee.repository;

import com.akshaya.elmsbackend.employee.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmployeeId(String employeeId);
    boolean existsByManager_IdAndActiveTrueAndIdNot(Long managerId, Long excludedEmployeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.employeeId = :employeeId")
    Optional<Employee> findForLeaveApplication(@Param("employeeId") String employeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findForDecision(@Param("id") Long id);
}
