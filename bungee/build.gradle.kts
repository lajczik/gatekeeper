plugins {
    id("java")
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(project(":shared"))
    compileOnly("net.md-5:bungeecord-api:1.21-R0.4")
}

tasks {
    shadowJar {
        minimize()
    }
}