dependencies {
    compileOnly(project(":tickbudget-api"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks.jar {
    archiveBaseName.set("TickBudgetTest")
}
