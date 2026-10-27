plugins {
    id("com.gradleup.shadow")
    id("com.modrinth.minotaur") version "2.10.0"
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

// Publishes the plugin jar to Modrinth. The token comes from the MODRINTH_TOKEN environment variable;
// pass -PmodrinthDryRun=true to print the payload without uploading anything.
modrinth {
    projectId.set("tickbudget")
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(providers.gradleProperty("modrinthGameVersions").get().split(","))
    loaders.addAll("paper", "folia", "purpur")
    changelog.set(
        providers.environmentVariable("MODRINTH_CHANGELOG")
            .orElse("See https://github.com/denfry/tickbudget/releases")
    )
    syncBodyFrom.set(rootProject.file("docs/modrinth/description.md").readText())
    debugMode.set(providers.gradleProperty("modrinthDryRun").map { it.toBoolean() }.orElse(false))
}
