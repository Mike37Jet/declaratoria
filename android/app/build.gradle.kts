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
        versionCode = 2
        versionName = "1.2"
    }

    // Las fotos de la página (public/photos) viajan dentro de la app: son las
    // primeras del carrusel la primera vez que se abre.
    sourceSets["main"].assets.srcDir("../../public")
    // La llave de Firebase ya no viaja en el APK: la guarda el servidor de avisos (../servidor).

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-messaging")
    // La libreta compartida: datos en Firestore y acceso anónimo (ver libreta/Libreta.kt).
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    // Resortes para la tapa, el elástico y el tachado.
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("androidx.work:work-runtime:2.10.2")
    // WorkManager usa ListenableFuture; con Firestore llega la versión vacía y hay que traer guava completa.
    implementation("com.google.guava:guava:33.6.0-android")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
