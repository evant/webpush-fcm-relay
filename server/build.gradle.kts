import io.ktor.plugin.features.DockerImageRegistry.Companion.dockerHub
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("io.ktor.plugin")
    application
}

group = "me.tatarka.webpush.relay"
version = "1.0.0"

ktor {
    fatJar {
        archiveFileName = "webpush-fcm-relay.jar"
    }
    docker {
        jreVersion = JavaVersion.VERSION_21
        localImageName = "etatarka/webpush-fcm-relay"
        imageTag = version.toString()

        externalRegistry = dockerHub(
            appName = provider { "webpush-fcm-relay" },
            username = project.providers.gradleProperty("dockerhub.username"),
            password = project.providers.gradleProperty("dockerhub.token")
        )
    }
}

application {
    mainClass = "me.tatarka.webpush.relay.ApplicationKt"
}

dependencies {
    implementation(libs.bundles.ktor)
    implementation(libs.webpush.encryption)
    implementation(libs.bundles.firebase.messaging.server)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.assertk)

    constraints {
        implementation("com.google.guava:guava:33.1.0-jre") {
            because("CVE-2023-2976")
        }
        implementation("io.netty:netty-codec-http2:4.1.108.Final") {
            because("CVE-2024-29025")
        }
    }
}

tasks.withType<JavaCompile> {
    options.release = 21
}
tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xjdk-release=21", "-Xjsr305=strict")
        jvmTarget = "21"
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs = listOf(
        "-Dio.ktor.development=true",
        "-Dlog.level=DEBUG"
    )
    args = listOf(
        "-port=8080",
        "-host=localhost",
        "-P:firebase.auth.credentialsDir=${rootProject.projectDir.resolve("credentials")}"
    )
}