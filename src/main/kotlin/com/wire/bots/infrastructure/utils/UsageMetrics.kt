package com.wire.bots.infrastructure.utils

import com.wire.bots.domain.DomainComponent
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

@DomainComponent
class UsageMetrics(
    registry: MeterRegistry
) {
    private val legacyHelpCommandCounter: Counter = Counter
        .builder("remindapp_legacy_help_commands_total")
        .description("Number of Legacy Help command received")
        .register(registry)

    private val helpCommandCounter: Counter = Counter
        .builder("remindapp_help_commands_total")
        .description("Number of Help command received")
        .register(registry)

    private val createCommandCounter: Counter = Counter
        .builder("remindapp_create_commands_total")
        .description("Number of Create command received")
        .register(registry)

    private val listCommandCounter: Counter = Counter
        .builder("remindapp_list_commands_total")
        .description("Number of List command received")
        .register(registry)

    private val deleteCommandCounter: Counter = Counter
        .builder("remindapp_delete_commands_total")
        .description("Number of Delete command received")
        .register(registry)

    fun onLegacyHelpCommand() {
        legacyHelpCommandCounter.increment()
    }

    fun onHelpCommand() {
        helpCommandCounter.increment()
    }

    fun onCreateCommand() {
        createCommandCounter.increment()
    }

    fun onListCommand() {
        listCommandCounter.increment()
    }

    fun onDeleteCommand() {
        deleteCommandCounter.increment()
    }
}
