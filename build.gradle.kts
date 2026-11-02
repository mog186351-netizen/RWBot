import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = "rw.bot"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

paperweight.reobfArtifactConfiguration = ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks {
    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
    }
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
