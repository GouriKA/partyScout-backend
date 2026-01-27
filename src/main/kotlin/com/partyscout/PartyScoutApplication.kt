package com.partyscout

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PartyScoutApplication

fun main(args: Array<String>) {
    runApplication<PartyScoutApplication>(*args)
}
