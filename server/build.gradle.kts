dependencies {
    implementation(project(":modelrenderer")) {
        isTransitive = false
    }
    implementation(project(":common"))

    implementation(libs.minestom)
    implementation(libs.joml)
    implementation(libs.adventure.minimessage)
    implementation(libs.bundles.polyglot)
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveFileName.set("server-$version.jar")
        mergeServiceFiles()

        manifest {
            attributes["Main-Class"] = "dev.munky.roguelike.server.MainKt"
        }
    }
}