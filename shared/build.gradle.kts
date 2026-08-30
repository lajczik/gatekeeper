plugins {
    id("java")
    id("com.gradleup.shadow")
}

dependencies {
    implementation("it.unimi.dsi:fastutil:8.5.18")
}

tasks {
    shadowJar {
        minimize()
        relocate("it.unimi.dsi.fastutil", "xyz.lychee.gatekeeper.libs.fastutil")
        relocate("com.grack.nanojson", "xyz.lychee.gatekeeper.libs.nanojson")
    }
}