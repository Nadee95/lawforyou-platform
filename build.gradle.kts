import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("org.springframework.boot") version "3.4.4" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    java
}

val springCloudVersion = "2024.0.1"
val nadeexCommonVersion = "0.1.0"
val nadeexExceptionVersion = "0.1.0"
val nadeexLoggingVersion = "0.1.0"

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
        mavenLocal()        // resolves nadeex-* libs published locally
        mavenCentral()
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

