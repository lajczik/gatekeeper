plugins {
    id("java")
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(project(":shared"))

    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
}

tasks {
    shadowJar {
        minimize()
    }
}