package mkt.config

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import java.lang.StringBuilder

data class RuleProp(val name: String, val value: Any)
data class RuleConfig(val ruleName: String, var active: Boolean, val props: MutableList<RuleProp> = mutableListOf())
data class RulesetConfig(
    val rulesetId: String,
    var active: Boolean,
    val ruleConfigs: MutableList<RuleConfig> = mutableListOf()
)

class DetectConfigGenerator private constructor() {
    private val rulesetConfigs = mutableListOf<RulesetConfig>()
    private val indent = 4

    companion object {
        private val defaultRulesets = setOf(
            "complexity",
            "style",
            "exceptions",
            "performance",
            "comments",
            "coroutines",
            "empty-blocks",
            "naming",
            "potential-bugs"
        )

        private fun yamlListToList(yamlList: YamlList): List<Any> {
            val list: MutableList<Any> = mutableListOf()
            for (item in yamlList.items) {
                val value = yamlNodeToConcreteValue(item)
                list.add(value)
            }
            return list
        }

        private fun yamlMapToMap(yamlMap: YamlMap): Map<String, Any> {
            val map: MutableMap<String, Any> = mutableMapOf()
            for ((k, v) in yamlMap.entries) {
                val value = yamlNodeToConcreteValue(v)
                map[k.content] = value
            }
            return map
        }

        private fun yamlNodeToConcreteValue(node: YamlNode): Any {
            return when (node) {
                is YamlScalar -> node.content
                is YamlList -> yamlListToList(node)
                is YamlMap -> yamlMapToMap(node)
                else -> TODO("unreachable")
            }
        }

        private fun extractRule(rulesetId: String, ruleName: String, config: YamlMap, writer: DetectConfigGenerator) {
            val ruleConfig = RuleConfig(ruleName, active = false)
            val ruleProps = ruleConfig.props

            for (ruleProp in config.entries) {
                val propKey = ruleProp.key.content
                val propValue = ruleProp.value
                if (propKey == "active") {
                    val isActive = (propValue as YamlScalar).content.lowercase() == "true"
                    ruleConfig.active = isActive
                } else {
                    // This is a rule-specific property.
                    val prop = RuleProp(propKey, yamlNodeToConcreteValue(propValue))
                    ruleProps.add(prop)
                }
            }

            writer.addRule(rulesetId, ruleConfig)
        }

        private fun extractRulesets(config: YamlMap, writer: DetectConfigGenerator) {
            val defaultRsConfigs = config.entries.filter { it.key.content in defaultRulesets }
            for (rulesetConfig in defaultRsConfigs) {
                val rulesetId = rulesetConfig.key.content
                writer.addRuleset(rulesetId)

                for (configEntry in (rulesetConfig.value as YamlMap).entries) {
                    val configKey = configEntry.key.content
                    val configValue = configEntry.value
                    if (configKey == "active") {
                        val isActive = (configValue as YamlScalar).content.lowercase() == "true"
                        writer.setRulesetState(rulesetId, isActive)
                    } else {
                        // This is config for a Rule in this ruleset.
                        extractRule(rulesetId, ruleName = configKey, configValue as YamlMap, writer)
                    }
                }
            }
        }

        fun new(userConfigs: List<YamlNode> = listOf()): DetectConfigGenerator {
            val writer = DetectConfigGenerator()

            for (config in userConfigs) {
                when (config) {
                    is YamlMap -> extractRulesets(config, writer)
                    else -> TODO("Handle $config")
                }
            }

            return writer
        }
    }

    fun addRuleset(
        rulesetId: String,
        active: Boolean = false,
        ruleConfigs: MutableList<RuleConfig> = mutableListOf()
    ): RulesetConfig {
        val rsConfig = RulesetConfig(rulesetId, active, ruleConfigs)
        rulesetConfigs.add(rsConfig)
        return rsConfig
    }

    fun addOrGetRuleset(
        rulesetId: String,
        active: Boolean = false,
        ruleConfigs: MutableList<RuleConfig> = mutableListOf()
    ): RulesetConfig {
        return rulesetConfigs.firstOrNull { it.rulesetId == rulesetId } ?: addRuleset(rulesetId, active, ruleConfigs)
    }

    fun setRulesetState(rulesetId: String, active: Boolean) {
        val target = addOrGetRuleset(rulesetId)
        target.active = active
    }

    fun addRule(
        rulesetId: String,
        ruleConfig: RuleConfig
    ) {
        val ruleset = addOrGetRuleset(rulesetId)
        ruleset.ruleConfigs.add(ruleConfig)
    }

    private fun stringifyYamlList(propValue: List<*>, currentLevel: Int): String {
        val builder = StringBuilder()
        builder.append("\n")
        val level = currentLevel + 1
        for (item in propValue) {
            val valueString = stringifyPropValue(item!!, level)
            val allTheLines = valueString.split("\n")
            builder.append(" ".repeat(indent * level))
            builder.append("-\n")
            for (line in allTheLines) {
                builder.append(" ".repeat((indent * level) + 1))
                builder.append(line)
                builder.append("\n")
            }
            builder.append("\n")
        }

        return builder.toString()
    }

    private fun stringifyYamlMap(propValue: Map<*, *>, currentLevel: Int): String {
        val builder = StringBuilder()
        val level = currentLevel + 1

        for ((key, value) in propValue) {
            builder.append(" ".repeat(indent * level))
            val valueString = stringifyPropValue(value!!, level)
            builder.append("${key.toString()}: $valueString\n")
        }

        return builder.toString()
    }


    private fun quotesRequired(propValue: String): Boolean {
        val maybeNumber = propValue.toDoubleOrNull()
        val maybeBool = propValue.toBooleanStrictOrNull()
        return maybeNumber == null && maybeBool == null
    }

    private fun stringifyPropValue(propValue: Any, currentLevel: Int): String {
        return when (propValue) {
            is String -> if (quotesRequired(propValue)) "'$propValue'" else propValue.toString()
            is List<*> -> stringifyYamlList(propValue, currentLevel)
            is Map<*, *> -> stringifyYamlMap(propValue, currentLevel)
            else -> "" // skipcq: TCV-001
        }
    }

    private fun stringifyRuleProp(prop: RuleProp, currentLevel: Int): String {
        val builder = StringBuilder(" ".repeat(indent * currentLevel))
        val (propName, propValue) = prop
        val propValueString = stringifyPropValue(propValue, currentLevel)
        builder.append("$propName: $propValueString\n")
        return builder.toString()
    }

    private fun stringifyRuleConfig(ruleConfig: RuleConfig, currentLevel: Int): String {
        val builder = StringBuilder()
        builder.append("${ruleConfig.ruleName}:\n")

        val level = currentLevel + 1
        builder.append(" ".repeat(indent * level))
        builder.append("active: ${ruleConfig.active}\n")

        for (prop in ruleConfig.props) {
            val rulePropString = stringifyRuleProp(prop, level)
            builder.append(rulePropString)
        }

        return builder.toString()
    }

    fun stringifyConfig(): String {
        val builder = StringBuilder()
        val level = 1
        for (rulesetConfig in rulesetConfigs) {
            builder.append("${rulesetConfig.rulesetId}:\n")
            builder.append(" ".repeat(indent * level))
            builder.append("active: ${if (rulesetConfig.active) "true" else "false"}\n")

            for (ruleConfig in rulesetConfig.ruleConfigs) {
                val ruleConfigStr = stringifyRuleConfig(ruleConfig, level)
                builder.append(" ".repeat(indent * level))
                builder.append(ruleConfigStr)
            }
        }

        return builder.toString()
    }
}
