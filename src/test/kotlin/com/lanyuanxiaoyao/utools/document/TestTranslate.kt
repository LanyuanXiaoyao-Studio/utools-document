package com.lanyuanxiaoyao.utools.document

import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    val html = Files.readAllBytes(Paths.get("D:\\Document\\project\\utools-document\\build\\out\\kotlin-reference\\pages\\10 - Kotlin 1.4.20.html")).toString(Charsets.UTF_8)
    println(Utils.translateHtml(html))
}
