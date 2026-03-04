package com.partyscout.shared.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Logs HTTP request/response pairs with timing, status code, and sanitized metadata.
 *
 * Security considerations:
 * - Request bodies are NOT logged (may contain PII, passwords, tokens)
 * - Query parameters are NOT logged (may contain API keys or user data)
 * - Only method, path, status, and duration are logged
 * - User-Agent is logged for client debugging but never contains PII
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RequestLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startTime = System.currentTimeMillis()

        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            val status = response.status

            if (shouldLog(request)) {
                log.info(
                    "HTTP {} {} status={} duration={}ms clientIp={} userAgent={}",
                    request.method,
                    request.requestURI,
                    status,
                    duration,
                    maskClientIp(request.remoteAddr),
                    truncateUserAgent(request.getHeader("User-Agent"))
                )

                if (status >= 500) {
                    log.warn(
                        "Server error: {} {} returned {} in {}ms",
                        request.method,
                        request.requestURI,
                        status,
                        duration
                    )
                }
            }
        }
    }

    /**
     * Skip logging for health checks, actuator, and static assets to reduce noise.
     */
    private fun shouldLog(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return !uri.startsWith("/actuator") &&
                !uri.startsWith("/health") &&
                !uri.equals("/favicon.ico")
    }

    /**
     * Mask the last octet of IPv4 addresses for privacy.
     * "192.168.1.42" -> "192.168.1.***"
     */
    private fun maskClientIp(ip: String?): String {
        if (ip == null) return "unknown"
        val parts = ip.split(".")
        if (parts.size == 4) {
            return "${parts[0]}.${parts[1]}.${parts[2]}.***"
        }
        // IPv6 or other — mask the last segment
        val segments = ip.split(":")
        if (segments.size > 1) {
            return segments.dropLast(1).joinToString(":") + ":****"
        }
        return "***"
    }

    /**
     * Truncate overly long User-Agent strings.
     */
    private fun truncateUserAgent(userAgent: String?): String {
        if (userAgent == null) return "-"
        return if (userAgent.length > 100) userAgent.take(100) + "..." else userAgent
    }
}
