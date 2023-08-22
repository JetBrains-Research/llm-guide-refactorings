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
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.mongodb:mongodb-driver-sync:4.9.0") // added this line for MongoDB driver
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.6.0.202305301015-r")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation(project(":common"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("myRun") {
    main = "FireHouseCliKt"  // Note the "Kt" suffix for top-level functions in Kotlin
    classpath = sourceSets["main"].runtimeClasspath
    val arguments = if (project.hasProperty("appArgs")) project.property("appArgs") as String else ""
    args(arguments.split(" "))
}