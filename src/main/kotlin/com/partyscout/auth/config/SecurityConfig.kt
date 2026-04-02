package com.partyscout.auth.config

import com.partyscout.auth.filter.FirebaseAuthFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.HttpStatusEntryPoint

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val firebaseAuthFilter: FirebaseAuthFilter
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // Disable CSRF — stateless JWT auth, no session cookies
            .csrf { it.disable() }
            // No sessions — each request is verified by Firebase token
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Return 401 (not redirect to login) for unauthorized requests
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Allow CORS preflight through without auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // Auth endpoints
                    .requestMatchers("/api/v2/auth/**").authenticated()
                    // Saved events and profiles require authentication
                    .requestMatchers("/api/v2/saved-events/**").authenticated()
                    .requestMatchers("/api/v2/profiles/**").authenticated()
                    // All other endpoints are public (wizard works anonymously)
                    .anyRequest().permitAll()
            }
            // Add Firebase token filter before the standard auth filter
            .addFilterBefore(firebaseAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
