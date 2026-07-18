import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("org.springframework.boot") version "3.4.4" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    java
}

val springCloudVersion = "2024.0.1"
val nadeexCommonVersion         = "0.1.0"
val nadeexExceptionVersion      = "0.3.0"
val nadeexLoggingVersion        = "0.1.0"
val nadeexSecurityVersion       = "0.3.1"
val nadeexMultitenancyVersion   = "0.1.0"
val nadeexObservabilityVersion  = "0.1.0"

// Expose to all subprojects so each service references a single source of truth
extra["nadeexCommonVersion"]        = nadeexCommonVersion
extra["nadeexExceptionVersion"]     = nadeexExceptionVersion
extra["nadeexLoggingVersion"]       = nadeexLoggingVersion
extra["nadeexSecurityVersion"]      = nadeexSecurityVersion
extra["nadeexMultitenancyVersion"]  = nadeexMultitenancyVersion
extra["nadeexObservabilityVersion"] = nadeexObservabilityVersion

val githubUser: String = (findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR") ?: ""
val githubToken: String = (findProperty("gpr.key") as String?) ?: System.getenv("GITHUB_TOKEN") ?: ""

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    group = "com.lawforyou"
    version = "0.1.0"

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenLocal()        // resolves nadeex-* libs published locally (local dev only)
        mavenCentral()
        // GitHub Packages — nadeex-* libraries (used in CI where mavenLocal() is empty)
        maven {
            name = "GitHubPackages-Common"
            url = uri("https://maven.pkg.github.com/Nadee95/nadeex-spring-common")
            credentials {
                username = githubUser
                password = githubToken
            }
        }
        maven {
            name = "GitHubPackages-Exception"
            url = uri("https://maven.pkg.github.com/Nadee95/nadeex-spring-exception")
            credentials {
                username = githubUser
                password = githubToken
            }
        }
        maven {
            name = "GitHubPackages-Logging"
            url = uri("https://maven.pkg.github.com/Nadee95/nadeex-spring-logging")
            credentials {
                username = githubUser
                password = githubToken
            }
        }
        maven {
            name = "GitHubPackages-Security"
            url = uri("https://maven.pkg.github.com/Nadee95/nadeex-spring-security")
            credentials {
                username = githubUser
                password = githubToken
            }
        }
        maven {
            name = "GitHubPackages-Multitenancy"
            url = uri("https://maven.pkg.github.com/Nadee95/nadeex-spring-multitenancy")
            credentials {
                username = githubUser
                password = githubToken
            }
        }
        maven {
            name = "GitHubPackages-Observability"
            url = uri("https://maven.pkg.github.com/Nadee95/nadeex-spring-observability")
            credentials {
                username = githubUser
                password = githubToken
            }
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom(SpringBootPlugin.BOM_COORDINATES)
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        }
    }

    dependencies {

        // Lombok — available in all subprojects
        compileOnly("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")
        testCompileOnly("org.projectlombok:lombok")
        testAnnotationProcessor("org.projectlombok:lombok")

        // Testing baseline
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.junit.jupiter:junit-jupiter")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

