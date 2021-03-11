@file:Import("https://dl.bintray.com/way-zer/maven/", mavenRepository = true)
@file:Import("cf.wayzer:PlaceHoldLib:3.2", mavenDependsSingle = true)
@file:Import("io.github.config4k:config4k:0.4.2", mavenDependsSingle = true)
@file:Import("com.typesafe:config:1.3.3", mavenDependsSingle = true)
@file:Import("org.slf4j:slf4j-simple:1.7.28", mavenDependsSingle = true)
@file:Import("org.jetbrains.exposed:exposed-core:0.29.1", mavenDepends = true)
@file:Import("org.jetbrains.exposed:exposed-dao:0.29.1", mavenDepends = true)
@file:Import("org.jetbrains.exposed:exposed-java-time:0.29.1", mavenDepends = true)
@file:Import("org.jetbrains.exposed:exposed-jdbc:0.29.1", mavenDepends = true)
@file:Import("com.h2database:h2:1.4.200", mavenDepends = true)
@file:Import("coreLibrary.lib.*", defaultImport = true)

name = "ScriptAgent 库模块"
/*
本模块实现一些平台无关的库
 */

generateHelper()