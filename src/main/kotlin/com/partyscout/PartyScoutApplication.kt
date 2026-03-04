package com.partyscout

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PartyScoutApplication

fun main(args: Array<String>) {
    runApplication<PartyScoutApplication>(*args)
}
