plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.4.2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")

    maven { url = uri("https://repo.codemc.io/repository/maven-releases/") }
    maven { url = uri("https://repo.codemc.io/repository/maven-snapshots/") }
}

dependencies {
    // LOMBOK <- NO BORRAR
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    testCompileOnly("org.projectlombok:lombok:1.18.32")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.32")

    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("fr.mrmicky:fastinv:3.1.2")

    testImplementation("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Paper supplies SQLite at runtime; integration tests need their own driver.
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.49.1.0")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
    testImplementation("me.clip:placeholderapi:2.11.6")

    testImplementation("com.github.seeseemelk:MockBukkit-v1.18:2.85.2")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    // A 1.18.x server runs on Java 17 or newer, so the plugin bytecode must stay 17.
    options.release.set(17)
    options.encoding = "UTF-8"
}

val runServerJava = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("com.google.gson", "site.zvolcan.fFAUtils.libs.gson")
        relocate("com.zaxxer.hikari", "site.zvolcan.fFAUtils.libs.hikari")
        relocate("fr.mrmicky.fastinv", "site.zvolcan.fFAUtils.fastinv")
    }

    runServer {
        minecraftVersion("1.18.2")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
        javaLauncher.set(runServerJava)
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
