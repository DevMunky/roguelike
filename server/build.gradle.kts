dependencies {
    implementation(project(":modelrenderer")) {
        isTransitive = false
    }
    implementation(project(":common")) {
        isTransitive = false
    }

    implementation(libs.minestom)
    implementation(libs.joml)
    implementation(libs.adventure.minimessage)
    implementation(libs.bundles.polyglot)
    implementation(libs.bundles.logback)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.bundles.kotlinx.coroutines)
}