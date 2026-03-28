rootProject.name = "lawforyou-platform"

include("config-server", "eureka-server", "user-service")

project(":config-server").projectDir = file("services/config-server")
project(":eureka-server").projectDir = file("services/eureka-server")
project(":user-service").projectDir  = file("services/user-service")

