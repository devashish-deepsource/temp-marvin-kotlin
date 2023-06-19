package mkt.config

import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
data class RuntimeConfig(
    val codePath: Path = System.getenv().getOrDefault(key = "CODE_PATH", defaultValue = "/code").let { Path.of(it) },
    val toolboxPath: Path = System.getenv().getOrDefault(key = "TOOLBOX_PATH", defaultValue = "/toolbox").let { Path.of(it) },
    val isOnPrem: Boolean = System.getenv("ON_PREM").equals("true", ignoreCase = true),
)
