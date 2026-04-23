rootProject.name = "lawforyou-platform"

include("config-server", "eureka-server", "user-service", "case-service", "api-gateway", "document-service")

project(":config-server").projectDir      = file("services/config-server")
project(":eureka-server").projectDir      = file("services/eureka-server")
project(":user-service").projectDir       = file("services/user-service")
project(":case-service").projectDir       = file("services/case-service")
project(":api-gateway").projectDir        = file("services/api-gateway")
project(":document-service").projectDir   = file("services/document-service")

