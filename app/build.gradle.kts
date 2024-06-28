plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "ru.karachinstar.diplom.gpstracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.karachinstar.diplom.gpstracker"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
    packagingOptions {
        exclude("META-INF/DEPENDENCIES")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    //ArcGis
    implementation("com.esri.arcgisruntime:arcgis-android:100.15.5")
    implementation("org.osgeo:proj4j:0.1.0")
   // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))


    // https://mvnrepository.com/artifact/org.codehaus.woodstox/stax2-api
    implementation("javax.xml.stream:stax-api:1.0-2")
    implementation("org.codehaus.woodstox:stax2-api:4.2.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
//    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.7")

}