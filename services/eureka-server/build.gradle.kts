plugins {
    // inherits spring boot + dependency-management from root
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}

tasks.named<Jar>("jar") {
    enabled = false
}

