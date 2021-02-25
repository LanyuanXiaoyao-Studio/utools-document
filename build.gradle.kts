plugins {
    kotlin("jvm") version "1.4.30"
    kotlin("plugin.serialization") version "1.4.30"
}

group = "com.lanyuanxiaoyao.utools"
version = "1.0.0"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies {
    implementation("com.hankcs:hanlp:portable-1.7.8")
    implementation("cn.hutool:hutool-all:5.5.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    implementation("org.jsoup:jsoup:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.9.0")
    implementation("org.seleniumhq.selenium:selenium-java:3.141.59")
    implementation(kotlin("stdlib"))
}
