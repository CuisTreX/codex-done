import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val keystorePropsFile = rootProject.file("keystore.properties")

// tools\run-tests.ps1 会从 ASCII Junction 路径跑测试（中文路径下 Gradle 的 test worker 加载不了测试类）。
// 同一个 build 目录被两种路径交替使用会让 AGP 的增量资源合并偶发失败，所以测试跑独立目录。
if (project.hasProperty("cxdTestRun")) {
    layout.buildDirectory.set(file("build-testrun"))
}

android {
    namespace = "com.cuistre.codexdone"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.cuistre.codexdone"
        minSdk = 26
        targetSdk = 33
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                val props = Properties()
                keystorePropsFile.inputStream().use { props.load(it) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // 本机只能用 JDK 11 跑 Gradle，而 androidx 的 lint 规则包是 Java 17 编译的，
    // lint 在 Java 11 上会 UnsupportedClassVersionError，这里跳过 lint 门槛。
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}

// 项目路径含中文（F:\CodeX\01-开发项目\...），Gradle 测试工作进程必须统一用 UTF-8，
// 否则 worker 读到的类路径会乱码，报 ClassNotFoundException。
tasks.withType<Test>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    systemProperty("file.encoding", "UTF-8")
}
