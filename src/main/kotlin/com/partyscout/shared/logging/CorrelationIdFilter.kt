package com.partyscout.shared.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Injects a correlation ID into every request for distributed tracing.
 *
 * If the client sends X-Correlation-Id, it is reused. Otherwise a new UUID is generated.
 * The ID is placed in SLF4J MDC so all log lines within the request automatically include it.
 * It is also returned in the response header so the client can reference it in bug reports.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {

    companion object {
        const val CORRELATION_ID_HEADER = "X-Correlation-Id"
        const val MDC_CORRELATION_ID = "correlationId"
        const val MDC_REQUEST_METHOD = "httpMethod"
        const val MDC_REQUEST_URI = "requestUri"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val correlationId = request.getHeader(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString()

        MDC.put(MDC_CORRELATION_ID, correlationId)
        MDC.put(MDC_REQUEST_METHOD, request.method)
        MDC.put(MDC_REQUEST_URI, request.requestURI)

        response.setHeader(CORRELATION_ID_HEADER, correlationId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_CORRELATION_ID)
            MDC.remove(MDC_REQUEST_METHOD)
            MDC.remove(MDC_REQUEST_URI)
        }
    }
}
