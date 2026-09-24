package com.akshaya.elmsbackend.employee.entity;

public record EmployeeDetailsResponse(
        String employeeId,
        String name,
        String designation,
        String department,
        String managerName,
        boolean manager
) {
}
