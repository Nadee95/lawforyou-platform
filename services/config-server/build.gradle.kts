plugins {
    // inherits spring boot + dependency-management from root
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-config-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}

// Disable plain jar — we only want the executable fat jar
tasks.named<Jar>("jar") {
    enabled = false
}

