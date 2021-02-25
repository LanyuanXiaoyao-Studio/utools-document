package com.lanyuanxiaoyao.utools.document

import com.hankcs.hanlp.HanLP
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
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

private val pathSet = Utils.pathSet("kotlin-reference")
private const val TEMP_TOC_FILENAME = "HelpTOC.json"

private fun downloadRToc() {
    val tocText = Utils.downloadText("https://kotlinlang.org/docs/HelpTOC.json")
    Utils.writeAndDeleteIfExists(pathSet.tempFile(TEMP_TOC_FILENAME), tocText)
}

private fun download() {
    Utils.browserWithClose(headless = false, proxy = true) { driver ->
        driver.manage().window().maximize()
        val tocText = Utils.readAndEmptyIfNonExists(pathSet.tempFile(TEMP_TOC_FILENAME))
        val toc = parseToc(tocText)
        val pages = toc.topLevelIds.mapNotNull { toc.entities.pages[it] }
        Utils.writeAndDeleteIfExists(
            pathSet.tempFolder("pages").join(pathSet.targetCss.fileName.toString()),
            Utils.readAndEmptyIfNonExists(pathSet.sourceCss)
        )
        parseItems(toc, pages)
            .filterNot { it.url.startsWith("http") }
            .filter { it.parsedUrl.isNotBlank() }
            .forEachIndexed { index, page ->
                if (index < 0) {
                    return@forEachIndexed
                }
                println("${page.parsedTitle} ${page.parsedUrl}")
                driver.get(page.parsedUrl)
                try {
                    WebDriverWait(driver, 10).until {
                        it.findElement(By.cssSelector(".kt-app__virtual-toc-sidebar"))
                    }
                } catch (e: Exception) {
                }
                val source = driver.pageSource
                    .replace("static/v3/app.css", "style.css")
                    .replace("src=\"images/", "src=\"https://kotlinlang.org/docs/images/")
                Utils.writeAndDeleteIfExists(pathSet.tempFolder("pages").join(page.url), source)
            }
    }
}

private fun handle() {
    val tocText = Utils.readAndEmptyIfNonExists(pathSet.tempFile(TEMP_TOC_FILENAME))
    val toc = parseToc(tocText)
    val pages = toc.topLevelIds.mapNotNull { toc.entities.pages[it] }
    Utils.writeAndDeleteIfExists(pathSet.targetCss, Utils.readAndEmptyIfNonExists(pathSet.sourceCss))
    val tempIndexesFolderPath = pathSet.tempFolder("indexes")
    parseItems(toc, pages)
        .filterNot { it.url.startsWith("http") }
        .filter { it.parsedUrl.isNotBlank() }
        .forEachIndexed { index, page ->
            if (index < 0) {
                return@forEachIndexed
            }
            val shortcuts = mutableListOf<Shortcut>()
            val source = Utils.readAndEmptyIfNonExists(pathSet.tempFolder("pages").join(page.url))
            val document = Jsoup.parse(source)
            document.select(".kt-app__sidebar").forEach { it.remove() }
            document.select(".kt-app__header").forEach { it.remove() }
            document.select(".kt-app__footer").forEach { it.remove() }
            document.select(".feedback").forEach { it.remove() }
            document.select(".navigation-links").forEach { it.remove() }
            // document.select(".kt-app__virtual-toc-sidebar").forEach { it.remove() }
            document.select(".jetbrains-cookies-banner").forEach { it.remove() }
            document.select("script").forEach { it.remove() }
            document.select(".run-button").forEach { it.remove() }
            document.select("ul.kt-app__virtual-toc > li > a.toc-item--anchor")
                .forEach {
                    val href = it.attr("href")
                    val newHref = href.split("#")[1]
                    it.attr("href", "#$newHref")
                }
            val authorInfo = Element("div")
                .addClass("author-desc no-trans")
                .attr("style", "text-align: 'center'; color: 'darkgray'; font-size: 'smaller'")
                .html(Utils.authorInfoTranslated(page.parsedUrl))
            document.selectFirst(".layout--scroll-container")
                .insertChildren(0, authorInfo)
            val translatedSource = Utils.translateHtml(document.outerHtml())
            val filename = page.url
            val path = Paths.get(pathSet.pages.toString(), filename)
            Utils.writeAndDeleteIfExists(path, translatedSource)

            val tempIndexPath = tempIndexesFolderPath.join("${page.url}.json")
            val doc = Jsoup.parse(translatedSource)
            val text = doc
                .select(".trans-p")
                .text()
            shortcuts.add(
                Shortcut(
                    page.title,
                    HanLP.getSummary(text, 500).replace(Regex("^。$"), ""),
                    "pages/$filename"
                )
            )
            page.anchors.forEach { anchorId ->
                val anchor = toc.entities.anchors[anchorId] ?: return@forEach
                val anchorText = doc.selectFirst(anchor.anchor)?.select(".trans-p")?.text() ?: ""
                shortcuts.add(
                    Shortcut(
                        anchor.title,
                        if (anchorText.isBlank()) "" else HanLP.getSummary(anchorText, 500)
                            .replace(Regex("。$"), ""),
                        "pages/${anchor.url}${anchor.anchor}"
                    )
                )
            }
            Utils.writeAndDeleteIfExists(tempIndexPath, Json.encodeToString(shortcuts))
        }
}

private fun indexes() {
    val tocText = Utils.readAndEmptyIfNonExists(pathSet.tempFile(TEMP_TOC_FILENAME))
    val toc = parseToc(tocText)
    val pages = toc.topLevelIds.mapNotNull { toc.entities.pages[it] }
    val tempIndexesFolderPath = pathSet.tempFolder("indexes")
    val shortcuts = mutableListOf<Shortcut>()
    parseItems(toc, pages)
        .filterNot { it.url.startsWith("http") }
        .filter { it.parsedUrl.isNotBlank() }
        .forEachIndexed { index, page ->
            val tempIndexPath = tempIndexesFolderPath.join("${page.url}.json")
            val text = Utils.readAndEmptyIfNonExists(tempIndexPath)
            val indexes = Json.decodeFromString(ListSerializer(Shortcut.serializer()), text)
            shortcuts.addAll(indexes)
        }
    Utils.writeAndDeleteIfExists(pathSet.indexes, Json.encodeToString(shortcuts))
}

fun main() {
    // downloadRToc()
    // download()
    handle()
    // indexes()
}
