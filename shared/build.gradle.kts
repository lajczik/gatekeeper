plugins {
    id("java")
    id("com.gradleup.shadow")
}

tasks {
    shadowJar {
        minimize()
    }
}