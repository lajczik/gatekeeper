plugins {
    id("java")
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(project(":shared"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}

tasks {
    shadowJar {
        minimize()
    }
}