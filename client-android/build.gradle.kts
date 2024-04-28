plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
    signing
}

version = "1.0.0"

android {
    compileSdk = 34
    namespace = "me.tatarka.webpush.relay"

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    api(libs.webpush.encryption)
    api(libs.firebase.messaging)
}

group = "me.tatarka.webpush.relay"
version = "1.0.1-SNAPSHOT"

publishing {
    publications {
        create<MavenPublication>("release") {
            artifactId = "client-android"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("client-android")
                description.set("Library for receiving pushes sent by webpush-fcm-relay")
                url.set("https://github.com/evant/webpush-fcm-relay")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("evant")
                        name.set("Eva Tatarka")
                    }
                }
                scm {
                    connection.set("https://github.com/evant/webpush-fcm-relay.git")
                    developerConnection.set("https://github.com/evant/webpush-fcm-relay.git")
                    url.set("https://github.com/evant/webpush-fcm-relay")
                }
            }
        }
    }
}

signing {
    setRequired {
        findProperty("signing.keyId") != null
    }

    publishing.publications.all {
        sign(this)
    }
}
