plugins {
    id("java")
    id("org.jetbrains.changelog") version "2.0.0"
    id("org.jetbrains.intellij") version "1.12.0"
    kotlin("jvm") version "1.8.21"
}

fun properties(key: String) = project.findProperty(key).toString()
group = properties("pluginGroup")
version = properties("pluginVersion")

intellij {
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))
}

repositories {
    mavenCentral()
}


dependencies {
    implementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mongodb:mongodb-driver-sync:4.9.0") // added this line for MongoDB driver
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:6.6.0.202305301015-r")
    implementation("org.mongodb:mongodb-driver-sync:4.9.0") // added this line for MongoDB driver
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.6.0.202305301015-r")
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":common"))
}

kotlin {
    jvmToolchain(17)
}

//tasks.register<JavaExec>("myRun") {
//    main = "FireHouseCliKt"  // Note the "Kt" suffix for top-level functions in Kotlin
//    classpath = sourceSets["main"].runtimeClasspath
//    val arguments = if (project.hasProperty("appArgs")) project.property("appArgs") as String else ""
//    args(arguments.split(" "))
//}
