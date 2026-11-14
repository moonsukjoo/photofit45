// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

val restoreDebugKeystore by tasks.registering {
  val rootDirPath = layout.projectDirectory
  doLast {
    val keystore = rootDirPath.file("debug.keystore").asFile
    val base64File = rootDirPath.file("debug.keystore.base64").asFile
    if (!keystore.exists()) {
      if (base64File.exists()) {
        val cleaned = base64File.readText().replace("\\s".toRegex(), "")
        keystore.writeBytes(java.util.Base64.getDecoder().decode(cleaned))
        logger.lifecycle("Restored debug.keystore from debug.keystore.base64")
      } else {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val keytoolName = if (isWindows) "keytool.exe" else "keytool"
        val keytoolFile = File(System.getProperty("java.home"), "bin/$keytoolName")
        val keytoolCmd = if (keytoolFile.exists()) keytoolFile.absolutePath else keytoolName
        val process = ProcessBuilder(
          keytoolCmd,
          "-genkeypair",
          "-noprompt",
          "-keystore", keystore.absolutePath,
          "-storepass", "android",
          "-alias", "androiddebugkey",
          "-keypass", "android",
          "-keyalg", "RSA",
          "-keysize", "2048",
          "-validity", "10000",
          "-dname", "CN=Android Debug,O=Android,C=US"
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
          throw GradleException("Failed to generate debug.keystore: $output")
        }
        logger.lifecycle("Generated debug.keystore automatically via keytool")
      }
    }
  }
}

subprojects {
  tasks.matching { it.name == "validateSigningDebug" }.configureEach {
    dependsOn(restoreDebugKeystore)
  }
}
