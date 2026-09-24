package com.akshaya.elmsbackend.auth;

import com.akshaya.elmsbackend.employee.entity.Employee;
import com.akshaya.elmsbackend.employee.entity.EmployeeCredential;
import com.akshaya.elmsbackend.employee.repository.EmployeeCredentialRepository;
import com.akshaya.elmsbackend.employee.repository.EmployeeRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeCredentialRepository employeeCredentialRepository;

    public CustomUserDetailsService(
            EmployeeRepository employeeRepository,
            EmployeeCredentialRepository employeeCredentialRepository) {

        this.employeeRepository = employeeRepository;
        this.employeeCredentialRepository = employeeCredentialRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        Employee employee = employeeRepository
                .findByEmployeeId(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Invalid employee ID or password"
                        )
                );

        EmployeeCredential credential = employeeCredentialRepository
                .findById(employee.getId())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Invalid employee ID or password"
                        )
                );

        return User
                .withUsername(employee.getEmployeeId())
                .password(credential.getPasswordHash())
                .disabled(!credential.isEnabled())
                .authorities(Collections.emptyList())
                .build();
    }
}