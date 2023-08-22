plugins {
    id("java")
    id("org.jetbrains.changelog") version "2.0.0"
    id("org.jetbrains.intellij") version "1.12.0"
    kotlin("jvm") version "1.8.21"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

fun properties(key: String) = project.findProperty(key).toString()
group = properties("pluginGroup")
version = properties("pluginVersion")

intellij {
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}


dependencies {
    implementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mongodb:mongodb-driver-sync:4.9.0") // added this line for MongoDB driver
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:6.6.0.202305301015-r")
    implementation(kotlin("stdlib-jdk8"))
}