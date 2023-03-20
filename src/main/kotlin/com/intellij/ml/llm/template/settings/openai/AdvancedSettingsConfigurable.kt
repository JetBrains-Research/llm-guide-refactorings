package com.intellij.ml.llm.template.settings.openai

import com.intellij.ml.llm.template.LLMBundle
import com.intellij.ml.llm.template.settings.LLMSettingsManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindValue
import com.intellij.ui.dsl.builder.panel

class AdvancedSettingsConfigurable : BoundConfigurable(LLMBundle.message("settings.configurable.openai.advanced.display.name")) {
    private val settings = service<LLMSettingsManager>()

    override fun createPanel(): DialogPanel {
        return panel {
            row(LLMBundle.message("settings.configurable.option.prompt.length.label")) {
                intTextField(64..16384)
                    .bindIntText(settings.state.openAi::promptLength)
            }
            row(LLMBundle.message("settings.configurable.option.suffix.length.label")) {
                intTextField(0..16384)
                    .bindIntText(settings.state.openAi::suffixLength)
            }
            row(LLMBundle.message("settings.configurable.option.max.tokens.label")) {
                intTextField(5..256)
                    .bindIntText(settings.state.openAi::maxTokens)
            }
            row(LLMBundle.message("settings.configurable.option.top.p.label")) {
                spinner(0.0..1.0, step = 0.1).bindValue(
                    settings::getTopP, settings::setTopP
                )
            }
            row(LLMBundle.message("settings.configurable.option.temperature.label")) {
                spinner(0.0..1.0, step = 0.1).bindValue(
                    settings::getTemperature, settings::setTemperature
                )
            }
            row(LLMBundle.message("settings.configurable.option.number.of.suggestions.label")) {
                spinner(0..5)
                    .bindIntValue(settings.state.openAi::numberOfSamples)
            }
            row(LLMBundle.message("settings.configurable.option.presence.penalty.label")) {
                spinner(0.0..1.0, step = 0.1).bindValue(
                    settings::getPresencePenalty, settings::setPresencePenalty
                )
            }
            row(LLMBundle.message("settings.configurable.option.frequency.penalty.label")) {
                spinner(0.0..1.0, step = 0.1).bindValue(
                    settings::getFrequencyPenalty, settings::setFrequencyPenalty
                )
            }
        }
    }
}