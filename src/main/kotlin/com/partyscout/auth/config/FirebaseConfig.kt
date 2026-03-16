package com.partyscout.auth.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream
import java.io.FileInputStream

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @Value("\${firebase.service-account-json:}")
    private lateinit var serviceAccountJson: String

    @Value("\${firebase.project-id:}")
    private lateinit var projectId: String

    @Bean
    fun firebaseApp(): FirebaseApp {
        if (FirebaseApp.getApps().isNotEmpty()) {
            logger.info("Firebase already initialized, reusing existing app")
            return FirebaseApp.getInstance()
        }

        val credentials = if (serviceAccountJson.isNotBlank()) {
            // Production: JSON from Secret Manager injected via env var
            logger.info("Initializing Firebase with service account JSON")
            GoogleCredentials.fromStream(ByteArrayInputStream(serviceAccountJson.toByteArray()))
        } else {
            // Local dev: use Application Default Credentials (gcloud auth application-default login)
            logger.info("Initializing Firebase with Application Default Credentials")
            GoogleCredentials.getApplicationDefault()
        }

        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .apply { if (projectId.isNotBlank()) setProjectId(projectId) }
            .build()

        return FirebaseApp.initializeApp(options)
    }
}
