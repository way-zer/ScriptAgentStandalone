repositories {
    mavenLocal()
    if (System.getProperty("user.timezone") == "Asia/Shanghai") {
        maven(url = "https://maven.aliyun.com/repository/public")
    }
    mavenCentral()
    if (System.getProperty("user.timezone") != "Asia/Shanghai")//ScriptAgent
        maven("https://maven.wayzer.workers.dev/")
    else {
        maven {
            url = uri("https://packages.aliyun.com/maven/repository/2102713-release-0NVzQH/")
            credentials {
                username = "609f6fb4aa6381038e01fdee"
                password = "h(7NRbbUWYrN"
            }
        }
    }
    maven(url = "https://www.jitpack.io")
}


dependencies {
    val libraryVersion = "1.7.4.1"
    val pluginImplementation by configurations
    pluginImplementation("cf.wayzer:ScriptAgent:$libraryVersion")
    pluginImplementation("cf.wayzer:LibraryManager:1.4.1")


    val implementation by configurations
    implementation(kotlin("script-runtime"))
    implementation("cf.wayzer:ScriptAgent:$libraryVersion")

    //coreLibrary
    implementation("cf.wayzer:PlaceHoldLib:3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    implementation("io.github.config4k:config4k:0.4.1")
    //coreLib/DBApi
    val exposedVersion = "0.29.1"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    //coreStandalone
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.2.1")
    implementation("org.jline:jline-terminal:3.19.0")
//    compile("org.jline:jline-terminal-jansi:3.19.0")
    implementation("org.jline:jline-reader:3.19.0")
    //ktor
    implementation("io.ktor:ktor-server-jetty:1.6.2")
    implementation("io.ktor:ktor-jackson:1.6.2")
    //mirai
    implementation("net.mamoe:mirai-core-api-jvm:2.5.0")
}