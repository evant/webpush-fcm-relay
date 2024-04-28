plugins {
    id("io.github.gradle-nexus.publish-plugin")
}

nexusPublishing {
    packageGroup = "me.tatarka.webpush.relay"
    repositories {
        sonatype()
    }
}
