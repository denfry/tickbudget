plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":tickbudget-paper"))
    implementation(project(":tickbudget-folia"))
    implementation("org.bstats:bstats-bukkit:3.1.0")
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks.shadowJar {
    archiveBaseName.set("TickBudget")
    archiveClassifier.set("")
    relocate("org.bstats", "dev.denfry.tickbudget.plugin.metrics.bstats")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
