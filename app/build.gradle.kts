plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    implementation("org.jgrapht:jgrapht-core:1.5.2")

    // JavaFX controls & media
    implementation("org.openjfx:javafx-controls:21")
    implementation("org.openjfx:javafx-media:21")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.guava)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    version = "21"
    modules = listOf(
        "javafx.controls",
        "javafx.media",
        "javafx.fxml"
    )
}

application {
    // Define the main class for the application.
    mainClass.set("app.Main")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

// Create a fat JAR with all dependencies
tasks.register<Jar>("fatJar") {
    manifest {
        attributes["Main-Class"] = "app.Main"
    }
    archiveBaseName.set("RestaurantSimulator")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}

// Add this to your existing build.gradle.kts file
application {
    mainClass.set("app.Main")
    
    // Configure the JVM arguments for JavaFX
    applicationDefaultJvmArgs = listOf(
        "--module-path=APP_HOME_PLACEHOLDER/lib",
        "--add-modules=javafx.controls,javafx.media,javafx.fxml"
    )
}

// Fix the placeholder in generated scripts
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        val windowsScript = file("${outputDir}/app.bat")
        val unixScript = file("${outputDir}/app")
        
        // Replace placeholder in Windows script
        val windowsScriptText = windowsScript.readText()
        val newWindowsScriptText = windowsScriptText.replace(
            "APP_HOME_PLACEHOLDER", "%APP_HOME%"
        )
        windowsScript.writeText(newWindowsScriptText)
        
        // Replace placeholder in Unix script
        val unixScriptText = unixScript.readText()
        val newUnixScriptText = unixScriptText.replace(
            "APP_HOME_PLACEHOLDER", "\$APP_HOME"
        )
        unixScript.writeText(newUnixScriptText)
    }
}