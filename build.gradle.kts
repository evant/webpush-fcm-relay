plugins {
    id("io.github.gradle-nexus.publish-plugin")
}

// need to be set for nexus publishing to work correctly.
version = libs.versions.client.android.get()

nexusPublishing {
    packageGroup = "me.tatarka.webpush.relay"
    repositories {
        sonatype()
    }
}
