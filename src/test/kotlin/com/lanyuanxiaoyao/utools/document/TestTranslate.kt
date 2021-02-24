package com.lanyuanxiaoyao.utools.document

import org.jsoup.Jsoup
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    val html = Files.readAllBytes(Paths.get("/Users/lanyuanxiaoyao/Project/IdeaProjects/utools-document/build/out/kotlin-reference/pages/9 - Kotlin 1.4.30.html"))
        .toString(Charsets.UTF_8)
    val document = Jsoup.parse(html)
    val elements = document.allElements
        .filterNot { listOf("code", "pre, title").contains(it.tagName()) }
        .filterNot { it.parents().map { p -> p.tagName() }.contains("pre") }
        .filterNot { it.parents().map { p -> p.tagName() }.contains("code") }
        .filter { it.ownText().isNotBlank() }
    val lines = elements.map { it.text().trim() }
    println(lines.size)
    println(Utils.splitList(lines) { list, _, last, now ->
        val count = list.subList(last, now + 1)
            .map { it.length }
            .sum()
        count > 3000
    }.map { it.size }.sum())
}
