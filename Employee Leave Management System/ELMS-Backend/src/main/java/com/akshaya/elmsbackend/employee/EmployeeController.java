package com.akshaya.elmsbackend.employee;

import com.akshaya.elmsbackend.employee.entity.EmployeeDetailsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    private final EmployeeService employeeService;
    
    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }
    
    @GetMapping("/me")
    public ResponseEntity<EmployeeDetailsResponse> getEmployeeDetails(Principal principal) {
        return ResponseEntity.ok(employeeService.getEmployeeDetails(principal.getName()));
    }
}
