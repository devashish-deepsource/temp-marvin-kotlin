package mkt

import com.charleskorn.kaml.Yaml
import io.gitlab.arturbosch.detekt.api.UnstableApi
import io.gitlab.arturbosch.detekt.cli.CliRunner
import mkt.config.AnalysisConfig
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.serialization.json.Json
import mkt.config.DetectConfigGenerator
import mkt.config.RuntimeConfig
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.lang.StringBuilder
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readLines
import kotlin.io.path.readText

val logger = KotlinLogging.logger("mkt")
val runtime = RuntimeConfig()

// Keeping it simple for now.
// We say that a YAML file is detekt config if either of these is true:
// 1. Name of the YAML file contains 'detekt'
// 2. First line of the YAML contains a comment that says 'detekt'.
fun searchDetektConfig(root: Path): List<Path> {
    return root.listDirectoryEntries()
        .filter { it.toString().endsWith(".yml") }
        .filter {
            it.fileName.toString().lowercase().contains("detekt") ||
            it.readLines().firstOrNull()?.lowercase()?.contains("detekt") ?: false
        }
}

fun writeDetektConfig(config: DetectConfigGenerator) {
    val content = config.stringifyConfig()
    val pathStr = runtime.toolboxPath.resolve("detekt.yml").toString()
    val conf = File(pathStr)
    try {
        conf.createNewFile()
        conf.writeText(content)
    } catch (e: IOException) {
        println(e.message)
    }
}

inline fun <reified T> toArray(list: List<*>): Array<T> {
    return (list as List<T>).toTypedArray()
}

@OptIn(UnstableApi::class)
fun main(args: Array<String>) {
    if (runtime.isOnPrem) {
        logger.info { "We're on-prem" }
    }

    val argParser = ArgParser("marvin-kotlin")
    val analysisConfigPath by argParser.option(ArgType.String, "analysis-config")
    argParser.parse(args)

    val analysisConfigJson = File(analysisConfigPath!!).readText()
    val analysisConfig = Json.decodeFromString<AnalysisConfig>(analysisConfigJson)

    // Search for user supplied detekt config YAMLs.
    val userConfigYamls = searchDetektConfig(runtime.codePath)
    val detektUserConfigs = userConfigYamls.map { Yaml.default.parseToYamlNode(it.readText()) }

    val configGen = DetectConfigGenerator.new(detektUserConfigs)
    writeDetektConfig(configGen)

    val confList = mutableListOf("--config", runtime.toolboxPath.resolve("detekt.yml").toString())
    confList.add("--input")
    val builder = StringBuilder()
    for (file in analysisConfig.files) {
        builder.append(file).append(",")
    }

    val detektArgs: Array<String> = toArray(confList)
    val rulesetViseFindings = CliRunner().run(detektArgs).container?.findings.orEmpty()

    for ((rulesetId, findings) in rulesetViseFindings) {
        for (finding in findings)
            logger.info {  finding }
    }
}
