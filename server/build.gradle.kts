dependencies {
    implementation(project(":modelrenderer")) {
        isTransitive = false
    }
    implementation(project(":common"))

    implementation(files("../schem/build/libs/schem-dev.jar"))

    implementation(libs.minestom)
    implementation(libs.adventure.minimessage)
    implementation(libs.joml)
    implementation(libs.bundles.polyglot)

    implementation(libs.bundles.logback)
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