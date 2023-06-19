package mkt.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Meta(
    val kotlin_version: Int
)

@Serializable
data class AnalyzerMeta(
    val name: String,
    val enabled: Boolean,
    val meta: Meta
)

/**
 * Kotlin analyzer input data holder.
 */
@Serializable
data class AnalysisConfig(
    val files: Collection<String>,

    @SerialName("exclude_patterns")
    val excludePatterns: Collection<String>,

    @SerialName("exclude_files")
    val excludeFiles: Collection<String>,

    @SerialName("test_files")
    val testFiles: Collection<String>,

    @SerialName("test_patterns")
    val testPatterns: Collection<String>?,

    @SerialName("analyzer_meta")
    val analyzerMeta: AnalyzerMeta,
)
