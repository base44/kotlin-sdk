plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = "com.base44"
version = "0.1.1"

kotlin {
    // JVM target (Android, server)
    jvm()

    // Apple targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()

    // Linux (native)
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        val appleMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        listOf(iosX64Main, iosArm64Main, iosSimulatorArm64Main, macosX64Main, macosArm64Main).forEach {
            it.get().dependsOn(appleMain)
        }

        val linuxMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        linuxX64Main.get().dependsOn(linuxMain)

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
