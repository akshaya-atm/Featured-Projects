package com.akshaya.elmsbackend.config;

import com.akshaya.elmsbackend.tool.ToolCallContext;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.security.Principal;
import java.util.Map;

@Configuration
public class McpTransportConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider authenticatedMcpTransportProvider(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
            McpServerStreamableHttpProperties properties) {

        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .disallowDelete(properties.isDisallowDelete())
                .contextExtractor(request -> {
                    Principal principal = request.principal()
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Authenticated MCP principal is unavailable"
                                    )
                            );

                    return McpTransportContext.create(
                            Map.of(
                                    ToolCallContext.EMPLOYEE_ID_KEY,
                                    principal.getName()
                            )
                    );
                })
                .build();
    }
}