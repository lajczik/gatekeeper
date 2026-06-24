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
    processResources {
        filesMatching("**/bungee.yml") {
            expand(rootProject.project.properties)
        }

        outputs.upToDateWhen { false }
    }
}