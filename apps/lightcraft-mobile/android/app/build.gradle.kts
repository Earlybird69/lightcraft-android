import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The Rust half of the app: this Gradle project is the android/ folder of the `lightcraft-mobile`
// crate (apps/lightcraft-mobile), which sits in the LightCraft cargo workspace two folders up.
val cargoWorkspaceDir: File = rootDir.parentFile.parentFile.parentFile
val rustAbis: List<String> =
    providers.gradleProperty("lightcraft.abis").getOrElse("arm64-v8a,x86_64").split(',').map { it.trim() }.filter { it.isNotEmpty() }
val minApi = 29

/** rustup's `cargo` (Android Studio may not see rustup's PATH), else `cargo` from PATH; `CARGO` overrides. */
val cargoExecutable: String = run {
    val exe = if (System.getProperty("os.name").lowercase().startsWith("windows")) "cargo.exe" else "cargo"
    val rustup = File(System.getProperty("user.home"), ".cargo/bin/$exe")
    providers.environmentVariable("CARGO").orNull ?: if (rustup.isFile) rustup.absolutePath else exe
}

/** Builds `liblightcraft_mobile.so` (release profile) for each ABI with cargo-ndk. */
abstract class CargoNdkBuild : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations
    @get:Input abstract val cargo: Property<String>
    @get:Input abstract val workspaceDir: Property<String>
    @get:Input abstract val ndkDir: Property<String>
    @get:Input abstract val abis: ListProperty<String>
    @get:Input abstract val apiLevel: Property<Int>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val targets = abis.get().flatMap { listOf("-t", it) }
        val out = outputDir.get().asFile.absolutePath
        execOperations.exec {
            workingDir(workspaceDir.get())
            environment("ANDROID_NDK_HOME", ndkDir.get())
            commandLine(
                listOf(cargo.get(), "ndk") + targets +
                    listOf("--platform", apiLevel.get().toString(), "-o", out, "build", "--release", "-p", "lightcraft-mobile", "--lib"),
            )
        }
    }
}

/** Generates the Kotlin bindings from the library cargo built (UniFFI library mode). */
abstract class UniffiBindgen : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations
    @get:Input abstract val cargo: Property<String>
    @get:Input abstract val workspaceDir: Property<String>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val library: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        execOperations.exec {
            workingDir(workspaceDir.get())
            commandLine(
                cargo.get(), "run", "--quiet", "-p", "lightcraft-mobile-bindgen", "--",
                "generate", library.get().asFile.absolutePath, "--language", "kotlin", "--out-dir", out.absolutePath, "--no-format",
            )
        }
    }
}

/**
 * Files from LightCraft's `assets/` the app ships, copied at build time rather than duplicated:
 * its app icon, its UI font Inter (OFL) and the font's licence. [targets] maps each file name to
 * its path in the generated resource (or asset) folder.
 */
abstract class LightcraftAssets : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val files: ConfigurableFileCollection
    @get:Input abstract val targets: MapProperty<String, String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        for (file in files) {
            val target = targets.get()[file.name] ?: continue
            file.copyTo(File(out, target), overwrite = true)
        }
    }
}

android {
    namespace = "ai.storyteller.lightcraft"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "ai.storyteller.lightcraft"
        minSdk = minApi
        targetSdk = 36
        versionCode = 1
        versionName = "0.4.0"
        ndk { abiFilters.addAll(rustAbis) }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // sign with your own key before publishing
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val cargoBuild = tasks.register<CargoNdkBuild>("cargoBuild$name") {
            group = "rust"
            description = "Builds liblightcraft_mobile.so for $rustAbis"
            cargo.set(cargoExecutable)
            workspaceDir.set(cargoWorkspaceDir.absolutePath)
            ndkDir.set(androidComponents.sdkComponents.ndkDirectory.map { it.asFile.absolutePath })
            abis.set(rustAbis)
            apiLevel.set(minApi)
            // cargo knows what changed
            outputs.upToDateWhen { false }
        }
        val bindgen = tasks.register<UniffiBindgen>("uniffiBindgen$name") {
            group = "rust"
            description = "Generates the Kotlin bindings of liblightcraft_mobile.so"
            cargo.set(cargoExecutable)
            workspaceDir.set(cargoWorkspaceDir.absolutePath)
            library.set(cargoBuild.flatMap { it.outputDir.file("${rustAbis.first()}/liblightcraft_mobile.so") })
        }
        val assetsDir = File(cargoWorkspaceDir, "assets")
        val res = tasks.register<LightcraftAssets>("lightcraftResources$name") {
            val map = mapOf(
                "app-icon/lightcraft-1024.png" to "drawable-nodpi/ic_launcher_tile.png",
                "fonts/Inter-Regular.ttf" to "font/inter_regular.ttf",
                "fonts/Inter-Medium.ttf" to "font/inter_medium.ttf",
                "fonts/Inter-SemiBold.ttf" to "font/inter_semibold.ttf",
            )
            files.from(map.keys.map { File(assetsDir, it) })
            targets.set(map.mapKeys { (k, _) -> k.substringAfterLast('/') })
        }
        val licences = tasks.register<LightcraftAssets>("lightcraftLicences$name") {
            files.from(File(assetsDir, "fonts/OFL-Inter.txt"))
            targets.set(mapOf("OFL-Inter.txt" to "licences/OFL-Inter.txt"))
        }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(cargoBuild, CargoNdkBuild::outputDir)
        variant.sources.java?.addGeneratedSourceDirectory(bindgen, UniffiBindgen::outputDir)
        variant.sources.res?.addGeneratedSourceDirectory(res, LightcraftAssets::outputDir)
        variant.sources.assets?.addGeneratedSourceDirectory(licences, LightcraftAssets::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jna) { artifact { type = "aar" } }
    testImplementation(libs.junit)
}
