plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.jorge"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    implementation("com.google.firebase:firebase-admin:9.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    // gRPC discovers its transport through service files, so they must be merged.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    // Paper bundles its own copies of these, which would otherwise win at runtime.
    val libs = "com.jorge.homecenix.libs"
    relocate("com.google.common", "$libs.guava")
    relocate("com.google.thirdparty", "$libs.thirdparty")
    relocate("com.google.gson", "$libs.gson")
    relocate("io.netty", "$libs.netty")
    relocate("com.google.protobuf", "$libs.protobuf")
    relocate("org.apache.http", "$libs.apache.http")
    relocate("org.apache.commons", "$libs.apache.commons")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
