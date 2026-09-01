plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.serialization)
}

val cfgModuleId: String = providers.gradleProperty("module.id").get()
val cfgModuleName: String = providers.gradleProperty("module.name").get()
val cfgModuleAuthor: String = providers.gradleProperty("module.author").get()
val cfgModuleDescription: String = providers.gradleProperty("module.description").get()
val cfgXposedApiMin: Int = providers.gradleProperty("xposed.api.min").get().toInt()
val cfgXposedApiTarget: Int = providers.gradleProperty("xposed.api.target").get().toInt()

android {
    namespace = "eu.hxreborn.biometricapplock"
    compileSdk = 37

    defaultConfig {
        applicationId = cfgModuleId
        minSdk = 33
        targetSdk = 37

        versionCode = project.property("version.code").toString().toInt()
        versionName = project.property("version.name").toString()

        buildConfigField("int", "LIBXPOSED_API_VERSION", "$cfgXposedApiTarget")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    lint {
        disable +=
            setOf(
                "MissingApplicationIcon",
                "DataExtractionRules",
                "AllowBackup",
                "UnusedResources",
                "OldTargetApi",
            )
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            fun secret(name: String): String? =
                providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull

            val storeFilePath = secret("RELEASE_STORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            } else {
                logger.warn("RELEASE_STORE_FILE not found. Release signing is disabled.")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += listOf("META-INF/xposed/**")
            excludes +=
                listOf(
                    "META-INF/AL2.0",
                    "META-INF/LGPL2.1",
                    "META-INF/LICENSE*",
                    "META-INF/NOTICE*",
                    "META-INF/DEPENDENCIES",
                    "META-INF/*.version",
                    "META-INF/*.kotlin_module",
                    "kotlin/**",
                    "DebugProbesKt.bin",
                )
        }
    }
}

kotlin {
    jvmToolchain(21)
}

val ktlint: Configuration by configurations.creating

dependencies {
    ktlint(libs.ktlint.cli)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.material.motion.compose.core)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}

val ktlintCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Check Kotlin code style"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("src/**/*.kt")
}

val ktlintFormat by tasks.registering(JavaExec::class) {
    group = "formatting"
    description = "Auto-format Kotlin code style"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("-F", "src/**/*.kt")
}

abstract class BundleChangelog : DefaultTask() {
    @get:InputFile
    abstract val changelogFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val dest = outputDir.get().asFile
        dest.mkdirs()
        changelogFile.get().asFile.copyTo(dest.resolve("changelog.json"), overwrite = true)
    }
}

val bundleChangelog by tasks.registering(BundleChangelog::class) {
    changelogFile.set(rootProject.file("CHANGELOG.json"))
}

abstract class GenerateXposedModuleProp : DefaultTask() {
    @get:Input
    abstract val moduleId: Property<String>

    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val moduleAuthor: Property<String>

    @get:Input
    abstract val moduleDescription: Property<String>

    @get:Input
    abstract val moduleVersionName: Property<String>

    @get:Input
    abstract val moduleVersionCode: Property<Int>

    @get:Input
    abstract val moduleMinApiVersion: Property<Int>

    @get:Input
    abstract val moduleTargetApiVersion: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val xposedDir = outputDir.get().file("META-INF/xposed").asFile
        xposedDir.mkdirs()
        xposedDir.resolve("module.prop").writeText(
            """
            id=${moduleId.get()}
            name=${moduleName.get()}
            version=${moduleVersionName.get()}
            versionCode=${moduleVersionCode.get()}
            author=${moduleAuthor.get()}
            description=${moduleDescription.get()}
            minApiVersion=${moduleMinApiVersion.get()}
            targetApiVersion=${moduleTargetApiVersion.get()}
            staticScope=true
            exceptionMode=protective
            autoHotReload=true
            """.trimIndent() + "\n",
        )
        xposedDir.resolve("scope.list").writeText("system\n")
    }
}

val generateXposedModuleProp by tasks.registering(GenerateXposedModuleProp::class) {
    moduleId.set(cfgModuleId)
    moduleName.set(cfgModuleName)
    moduleAuthor.set(cfgModuleAuthor)
    moduleDescription.set(cfgModuleDescription)
    moduleVersionName.set(project.property("version.name").toString())
    moduleVersionCode.set(project.property("version.code").toString().toInt())
    moduleMinApiVersion.set(cfgXposedApiMin)
    moduleTargetApiVersion.set(cfgXposedApiTarget)
}

androidComponents {
    onVariants { variant ->
        variant.sources.resources?.addGeneratedSourceDirectory(
            generateXposedModuleProp,
            GenerateXposedModuleProp::outputDir,
        )
        variant.sources.assets?.addGeneratedSourceDirectory(
            bundleChangelog,
            BundleChangelog::outputDir,
        )
    }
}

tasks.named("preBuild").configure {
    dependsOn(ktlintFormat)
}

tasks.named("check").configure {
    dependsOn(ktlintCheck)
}
