package com.partyscout.chat

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@RestController
class ChatController(
    private val scoutAgentService: ScoutAgentService,
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)
    private val executor = Executors.newCachedThreadPool()

    @PostMapping("/api/chat")
    fun chat(@RequestBody request: ChatRequest): SseEmitter {
        val emitter = SseEmitter(120_000L)

        val cancelled = AtomicBoolean(false)
        emitter.onCompletion { cancelled.set(true) }
        emitter.onError { _ -> cancelled.set(true) }
        emitter.onTimeout {
            cancelled.set(true)
            try {
                emitter.send("Sorry, that took too long. Please try again.")
                emitter.complete()
            } catch (_: Exception) {}
        }

        executor.submit {
            try {
                scoutAgentService.runAgent(request, emitter, cancelled)
            } catch (e: Exception) {
                logger.error("Chat request failed: {}", e.message)
                if (!cancelled.get()) {
                    try {
                        emitter.send("Something went wrong. Please try again.")
                        emitter.complete()
                    } catch (_: Exception) {}
                }
            }
        }

        return emitter
    }

    // ── History sanitization (kept for reference / future use) ───────────────

    fun sanitizeHistory(history: List<ChatMessage>): List<ChatMessage> {
        return history.map { msg ->
            msg.copy(
                content = msg.content
                    .replace(Regex("\\[VENUES\\].*"), "[venue results shown]")
                    .trim()
            )
        }
    }
}
