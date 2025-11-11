import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

dependencies {
    implementation(project(":common"))
    implementation(libs.joml)
}

tasks {
    shadowJar {
        archiveBaseName.set("modelrenderer-$version.jar")
        mergeServiceFiles()
        minimize()
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}