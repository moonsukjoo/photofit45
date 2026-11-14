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
    if (!keystore.exists() && base64File.exists()) {
      val cleaned = base64File.readText().replace("\\s".toRegex(), "")
      keystore.writeBytes(java.util.Base64.getDecoder().decode(cleaned))
      logger.lifecycle("Restored debug.keystore from debug.keystore.base64")
    }
  }
}

subprojects {
  tasks.matching { it.name == "validateSigningDebug" }.configureEach {
    dependsOn(restoreDebugKeystore)
  }
}
