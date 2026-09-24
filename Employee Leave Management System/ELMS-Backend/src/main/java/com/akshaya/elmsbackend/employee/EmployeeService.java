package com.akshaya.elmsbackend.employee;

import com.akshaya.elmsbackend.employee.entity.Employee;
import com.akshaya.elmsbackend.employee.entity.EmployeeDetailsResponse;
import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.akshaya.elmsbackend.common.exception.AppException;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employees;

    public EmployeeService(EmployeeRepository employees) {
        this.employees = employees;
    }

    public EmployeeDetailsResponse getEmployeeDetails(String employeeId) {
        Employee employee = employees.findByEmployeeId(employeeId)
                .orElseThrow(() -> new AppException(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "Employee details not found"));

        if (!employee.isActive()) {
            throw new AppException(EmployeeErrorCode.EMPLOYEE_INACTIVE, "Employee account is inactive");
        }

        boolean manager = employees.existsByManager_IdAndActiveTrueAndIdNot(employee.getId(), employee.getId());

        return new EmployeeDetailsResponse(
                employee.getEmployeeId(),
                employee.getFullName(),
                employee.getDesignation(),
                employee.getDepartment().getName(),
                employee.getManager() == null ? null : employee.getManager().getFullName(),
                manager
        );
    }
}
