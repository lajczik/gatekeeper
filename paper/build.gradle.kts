plugins {
    id("java")
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(project(":shared"))
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")

    implementation("org.bstats:bstats-bukkit:3.1.0")
}

tasks {
    shadowJar {
        minimize()
    }
}