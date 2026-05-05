plugins {
    id("java")
    id("com.gradleup.shadow")
}

dependencies {
    implementation("com.grack:nanojson:1.10")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("com.h2database:h2:2.4.240")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.8")
}

tasks {
    shadowJar {
        minimize {
            exclude(dependency("com.zaxxer:HikariCP"))
            exclude(dependency("com.h2database:h2"))
            exclude(dependency("org.mariadb.jdbc:mariadb-java-client"))
        }
    }
}