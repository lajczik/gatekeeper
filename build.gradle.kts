plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.1"
}

group = "xyz.lychee.gatekeeper"
version = "1.0.7"

dependencies {
    implementation(project(":shared"))
    implementation(project(":velocity"))
    implementation(project(":bungee"))
    implementation(project(":bukkit"))
    implementation(project(":paper"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    shadowJar {
        archiveBaseName.set("Gatekeeper")
        archiveClassifier.set("")
        destinationDirectory.set(file("C:/Users/lajczi/Desktop/testowy/plugins"))

        relocate("org.bstats", "xyz.lychee.gatekeeper.libs.metrics")
        relocate("dev.dejvokep.boostedyaml", "xyz.lychee.gatekeeper.libs.yaml")
    }
}

allprojects {
    group = "xyz.lychee"

    apply(plugin = "java")
    apply(plugin = "com.gradleup.shadow")

    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        implementation("dev.dejvokep:boosted-yaml:1.3.7")

        //compileOnly("io.netty:netty-all:4.2.7.Final")
        compileOnly("org.projectlombok:lombok:1.18.46")
        annotationProcessor("org.projectlombok:lombok:1.18.46")
    }

    tasks {
        compileJava {
            options.encoding = "UTF-8"
        }

        processResources {
            filesMatching("**/plugin.yml") {
                expand(rootProject.project.properties)
            }
            filesMatching("**/bungee.yml") {
                expand(rootProject.project.properties)
            }
            filesMatching("**/velocity-plugin.json") {
                expand(rootProject.project.properties)
            }
            filesMatching("**/paper-plugin.yml") {
                expand(rootProject.project.properties)
            }

            outputs.upToDateWhen { false }
        }
    }
}