plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.1"
}

group = "xyz.lychee.gatekeeper"
version = "1.1"

dependencies {
    implementation(project(":shared", "shadow"))
    implementation(project(":velocity", "shadow"))
    implementation(project(":bungee", "shadow"))
    implementation(project(":bukkit", "shadow"))
    implementation(project(":paper", "shadow"))
}

tasks {
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

        compileOnly("org.projectlombok:lombok:1.18.46")
        annotationProcessor("org.projectlombok:lombok:1.18.46")
    }

    tasks {
        compileJava {
            options.encoding = "UTF-8"
            //options.release = 8
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

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    configurations {
        compileClasspath {
            attributes {
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
            }
        }
    }
}