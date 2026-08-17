plugins {
    id("java")
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(project(":shared"))
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    //annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT") plugin doesn't need this
}

tasks {
    shadowJar {
        minimize()
    }
    processResources {
        filesMatching("**/velocity-plugin.json") {
            expand(rootProject.project.properties)
        }

        outputs.upToDateWhen { false }
    }
}