package com.akshaya.shopsphere.common;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;

@WebFilter("/*")
public class CorsFilter implements Filter {

    // Explicit allow-list: reflecting an arbitrary Origin while allowing credentials would let
    // any website make authenticated requests on behalf of a logged-in user's browser.
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://127.0.0.1:5500",
            "http://localhost:5500"
    );

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String origin = httpRequest.getHeader("Origin");
        if (origin != null && (ALLOWED_ORIGINS.contains(origin) || origin.matches("^http://(localhost|127\\.0\\.0\\.1)(:\\d+)?$"))) {
            httpResponse.setHeader("Access-Control-Allow-Origin", origin);
            httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
            httpResponse.setHeader("Vary", "Origin");
        }
        // Otherwise no CORS headers are set and the browser blocks the response itself.

        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        httpResponse.setHeader("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Authorization");
        httpResponse.setHeader("Access-Control-Max-Age", "3600");

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        try {
            chain.doFilter(request, response);
        } catch (Throwable t) {
            t.printStackTrace();
            httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            httpResponse.setContentType("application/json");
            String cleanMsg = t.getMessage() != null ? t.getMessage().replace("\"", "'") : "Internal Server Error";
            httpResponse.getWriter().write("{\"success\": false, \"message\": \"" + cleanMsg + "\"}");
        }
    }

    @Override
    public void destroy() {
    }
}
