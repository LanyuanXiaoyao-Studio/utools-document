package com.lanyuanxiaoyao.utools.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.WebDriverWait
import java.nio.file.Paths

@Serializable
private data class Toc(
    val entities: Entities,
    val topLevelIds: List<String>,
)

@Serializable
private data class Entities(
    val pages: Map<String, Page>,
    val anchors: Map<String, Anchor>,
)

@Serializable
private data class Page(
    val id: String,
    val title: String,
    @SerialName("disqus_id")
    val disqusId: String = "",
    val url: String = "",
    val level: Int,
    val parentId: String,
    val anchors: List<String>,
    val tabIndex: Int,
) {
    val parents = mutableListOf<Page>()
    val parsedTitle: String
        get() = "${parents.joinToString(" / ") { it.title }}${if (parents.isEmpty()) "" else " / "}$title"
    val parsedUrl: String
        get() = if (url.startsWith("http")) url else if (url.isBlank()) "" else "https://kotlinlang.org/docs/${url}"

    override fun toString(): String {
        return "Page(id='$id', title='$title', disqusId='$disqusId', url='$url', level=$level, parentId='$parentId', anchors=$anchors, tabIndex=$tabIndex, parents=$parents, parsedTitle='$parsedTitle', parsedUrl='$parsedUrl')"
    }
}

@Serializable
private data class Anchor(
    val parentId: String,
    val level: Int,
    val id: String,
    val title: String,
    @SerialName("disqus_id")
    val disqusId: String = "",
    val anchor: String,
    val url: String = "",
)

private fun parseToc(text: String): Toc =
    Json { ignoreUnknownKeys = true; encodeDefaults = true }.decodeFromString(Toc.serializer(), text)

private fun parseItems(toc: Toc, pages: List<Page>, parent: Page? = null): List<Page> {
    val ps = mutableListOf<Page>()
    pages.forEach {
        ps.add(it)
        if (parent != null) {
            it.parents.addAll(parent.parents)
            it.parents.add(parent)
        }
        val childrenPages = toc.entities.pages.values.filter { p -> p.parentId == it.id }
        if (childrenPages.isNotEmpty()) {
            ps.addAll(parseItems(toc, childrenPages, it))
        }
    }
    return ps
}

fun main() {
    Utils.browserWithClose(proxy = true) {
        it.manage().window().maximize()
        val tocText = Utils.downloadText("https://kotlinlang.org/docs/HelpTOC.json")
        val toc = parseToc(tocText)
        val pages = toc.topLevelIds.mapNotNull { toc.entities.pages[it] }
        val pathSet = Utils.pathSet("kotlin-reference")
        Utils.writeAndDeleteIfExists(pathSet.targetCss, Utils.readAndEmptyIfNonExists(pathSet.sourceCss))
        parseItems(toc, pages)
            .filterNot { it.url.startsWith("http") }
            .filter { it.parsedUrl.isNotBlank() }
            .forEachIndexed { index, page ->
                if (index <= 9) {
                    return@forEachIndexed
                }
                println("${page.parsedTitle} ${page.parsedUrl}")
                it.get(page.parsedUrl)
                try {
                    WebDriverWait(it, 10).until { it.findElement(By.className("kt-app__sidebar")) }
                    it.executeScript("document.querySelector('.kt-app__sidebar').remove()")
                } catch (exception: Exception) {
                }
                try {
                    WebDriverWait(it, 10).until { it.findElement(By.className("kt-app__header")) }
                    it.executeScript("document.querySelector('.kt-app__header').remove()")
                } catch (exception: Exception) {
                }
                try {
                    WebDriverWait(it, 10).until { it.findElement(By.className("kt-app__footer")) }
                    it.executeScript("document.querySelector('.kt-app__footer').remove()")
                } catch (exception: Exception) {
                }
                try {
                    WebDriverWait(it, 10).until { it.findElement(By.className("feedback")) }
                    it.executeScript("document.querySelector('.feedback').remove()")
                } catch (exception: Exception) {
                }
                try {
                    WebDriverWait(it, 10).until { it.findElement(By.className("navigation-links")) }
                    it.executeScript("document.querySelector('.navigation-links').remove()")
                } catch (exception: Exception) {
                }
                try {
                    WebDriverWait(it, 10).until { it.findElement(By.className("kt-app__virtual-toc-sidebar")) }
                    it.executeScript("document.querySelector('.kt-app__virtual-toc-sidebar').remove()")
                } catch (exception: Exception) {
                }
                try {
                    WebDriverWait(it, 10).until { it.findElement(By.className("jetbrains-cookies-banner")) }
                    it.executeScript("document.querySelector(`.jetbrains-cookies-banner`).remove()")
                } catch (exception: Exception) {
                }
                it.executeScript("document.querySelectorAll('script').forEach(e => e.remove())")
                val source = it.pageSource
                    .replace("static/v3/app.css", "style.css")
                    .replace("src=\"images/", "src=\"https://kotlinlang.org/docs/images/")

                val path = Paths.get(pathSet.pages.toString(), "$index - ${page.title.replace("/", "-")}.html")
                Utils.writeAndDeleteIfExists(path, source)
            }
    }
}
