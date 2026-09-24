package com.akshaya.elmsbackend.config;

import com.akshaya.elmsbackend.tool.CompanyPolicyTools;
import com.akshaya.elmsbackend.tool.EmployeeTools;
import com.akshaya.elmsbackend.tool.ManagerTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider leaveToolCallbacks(
            EmployeeTools employeeTools,
            ManagerTools managerTools,
            CompanyPolicyTools companyPolicyTools) {

        return MethodToolCallbackProvider.builder()
                .toolObjects(employeeTools, managerTools, companyPolicyTools)
                .build();
    }
}
