import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.roguelito.rommicines"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.roguelito.rommicines"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Las fotos de la página (public/photos) viajan dentro de la app: son las
    // primeras del carrusel la primera vez que se abre.
    sourceSets["main"].assets.srcDir("../../public")
    // La llave de Firebase para mandar toques. Miguel decidió llevarla dentro
    // del APK (solo va a sus dos celulares); no se sube a GitHub (ver .gitignore).
    sourceSets["main"].assets.srcDir("secreto")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.work:work-runtime:2.10.2")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
