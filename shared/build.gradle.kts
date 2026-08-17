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
    }
}