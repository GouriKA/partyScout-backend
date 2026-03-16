package com.partyscout.auth.filter

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class FirebaseAuthFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(FirebaseAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val idToken = authHeader.removePrefix("Bearer ").trim()
            try {
                // checkRevoked=true ensures revoked tokens are rejected immediately
                val decodedToken: FirebaseToken = FirebaseAuth.getInstance()
                    .verifyIdToken(idToken, true)

                val auth = UsernamePasswordAuthenticationToken(
                    decodedToken,
                    null,
                    emptyList()
                )
                auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = auth
            } catch (ex: FirebaseAuthException) {
                // Don't set auth — Spring Security will reject if endpoint requires auth
                log.warn("Firebase token verification failed: {}", ex.authErrorCode)
            } catch (ex: Exception) {
                log.warn("Unexpected error verifying Firebase token: {}", ex.javaClass.simpleName)
            }
        }

        filterChain.doFilter(request, response)
    }
}
