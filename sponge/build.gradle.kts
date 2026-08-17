plugins {
    id("java")
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(project(":shared"))
    compileOnly("org.spongepowered:spongeapi:12.0.0")
}

tasks {
    shadowJar {
        minimize()
    }
    processResources {
        filesMatching("**/sponge_plugins.json") {
            expand(rootProject.project.properties)
        }

        outputs.upToDateWhen { false }
    }
}