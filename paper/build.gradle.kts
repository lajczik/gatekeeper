plugins {
    id("java")
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(project(":shared"))
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
}

tasks {
    shadowJar {
        minimize()
    }
    processResources {
        filesMatching("**/paper-plugin.yml") {
            expand(rootProject.project.properties)
        }

        outputs.upToDateWhen { false }
    }
}