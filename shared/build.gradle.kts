plugins {
    id("java")
    id("com.gradleup.shadow")
}

dependencies {
    implementation("com.grack:nanojson:1.10")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("com.h2database:h2:2.4.240")
    implementation("com.mysql:mysql-connector-j:9.6.0")
}

tasks {
    shadowJar {
        minimize()
    }
}