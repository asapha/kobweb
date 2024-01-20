// https://kotlinlang.org/docs/ksp-multiplatform.html

plugins { alias(libs.plugins.kotlin.multiplatform) }

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvm()

    sourceSets {
        jvmMain {
            dependencies {
                implementation("com.google.devtools.ksp:symbol-processing-api:1.9.21-1.0.15")
                implementation("com.squareup:kotlinpoet:1.16.0")
                implementation("com.squareup:kotlinpoet-ksp:1.16.0")
            }

            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }
    }
}

kotlin { jvmToolchain(21) }
