plugins {
    jacoco
}

val nadeexCommonVersion:    String by rootProject.extra
val nadeexExceptionVersion: String by rootProject.extra

dependencies {
    // Nadeex shared libraries
    implementation("com.nadeex.spring:common:$nadeexCommonVersion")
    implementation("com.nadeex.spring:exception:$nadeexExceptionVersion")

    // Spring Cloud Gateway (reactive — do NOT add spring-boot-starter-web)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")

    // Service discovery
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    // Config client
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    // Reactive security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // OAuth2 resource server — NimbusReactiveJwtDecoder for Keycloak RS256 JWKS validation
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Redis — rate limiting + token blacklist
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}


