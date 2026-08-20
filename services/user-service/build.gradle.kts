plugins {
    // inherits spring boot + dependency-management + java from root
    jacoco
}

val nadeexCommonVersion:         String by rootProject.extra
val nadeexExceptionVersion:     String by rootProject.extra
val nadeexLoggingVersion:       String by rootProject.extra
val nadeexSecurityVersion:      String by rootProject.extra
val nadeexMultitenancyVersion:  String by rootProject.extra
val nadeexObservabilityVersion: String by rootProject.extra

dependencies {
    // Nadeex shared libraries
    implementation("com.nadeex.spring:common:$nadeexCommonVersion")
    implementation("com.nadeex.spring:exception:$nadeexExceptionVersion")
    implementation("com.nadeex.spring:logging:$nadeexLoggingVersion")
    implementation("com.nadeex.spring:security:$nadeexSecurityVersion")
    implementation("com.nadeex.spring:multitenancy:$nadeexMultitenancyVersion")
    implementation("com.nadeex.spring:observability:$nadeexObservabilityVersion")

    // Web + Validation
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security (starter needed for DaoAuthenticationProvider, PasswordEncoder, etc.)
    implementation("org.springframework.boot:spring-boot-starter-security")


    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // DB Migration
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Cache
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Messaging
    implementation("org.springframework.kafka:spring-kafka")

    // Service Discovery
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    // Config Client
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    // Observability — actuator + prometheus come transitively from nadeex-observability
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

    // AOP (needed for @Loggable from nadeex-spring-logging)
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // MapStruct for DTO mapping
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")

    // OpenAPI / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("com.github.dasniko:testcontainers-keycloak:3.4.0")
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

// Packages excluded from JaCoCo — Lombok-generated boilerplate or entry-points not worth testing
val jacocoExclusions = listOf(
    "**/entity/**",                // JPA entities: @Data/@Getter/@Setter — all Lombok
    "**/UserServiceApplication.class", // Spring Boot main()
    "**/keycloak/impl/**",         // Keycloak Admin REST client — requires live Keycloak; covered by E2E tests
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(jacocoExclusions) }
        })
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(jacocoExclusions) }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.80".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}


