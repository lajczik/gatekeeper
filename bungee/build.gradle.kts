plugins {
    id("java")
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(project(":shared"))
    compileOnly("net.md-5:bungeecord-api:1.21-R0.4")

    implementation("org.bstats:bstats-bungeecord:3.1.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    shadowJar {
        minimize()
    }
}