package com.lanyuanxiaoyao.utools.document

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.jsoup.Jsoup
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    val documentUrl = "https://licia.liriliri.io/docs_cn.html"
    Utils.browserWithClose(headless = false, proxy = true) {
        it.get(documentUrl)
        val text = Jsoup.parse(it.pageSource)
            .selectFirst("body > div.content-wrapper > div > div.md.doc-container")
            .html()
        val items = text.split(Regex("(?=<h2\\s*id=\"(.+?)\">.+?</h2>)"))
        val client = Utils.httpClient(true)
        val shortcuts = mutableListOf<Shortcut>()
        val pathSet = Utils.pathSet("licia")
        Utils.writeAndDeleteIfExists(pathSet.targetCss, Utils.readAndEmptyIfNonExists(pathSet.sourceCss))
        items.subList(1, items.size)
            .forEachIndexed { index, page ->
                val doc = Jsoup.parse(page)
                val title = doc.selectFirst("h2[id]").text()
                println("Now handle: $index/${items.size}, $title")
                val desc = doc.selectFirst("h2[id] + p + p").text()
                doc.selectFirst("i.download")?.remove()
                doc.selectFirst("i.env")?.remove()
                doc.selectFirst("a.btn-repl")?.remove()
                val sourceUrl = "https://raw.githubusercontent.com/liriliri/licia/master/src/$title.js"
                println("Source url: $sourceUrl")
                val sourceRequest = Request.Builder().url(sourceUrl).build()
                val source = try {
                    client.newCall(sourceRequest).execute().body?.string() ?: ""
                } catch (e: Exception) {
                    ""
                }
                if (source.isBlank()) println("Source gather failure")
                val testUrl = "https://raw.githubusercontent.com/liriliri/licia/master/test/$title.js"
                println("Test url: $testUrl")
                val testRequest = Request.Builder().url(testUrl).build()
                val test = try {
                    client.newCall(testRequest).execute().body?.string() ?: ""
                } catch (e: Exception) {
                    ""
                }
                if (test.isBlank()) println("Test is blank")
                val path = Paths.get(pathSet.pages.toString(), "$title.html")
                // language=HTML
                val html =
                    "<html lang=\"zh\">\n<head>\n    <meta charset=\"UTF-8\">\n    <title>$title</title>\n    <link type=\"text/css\" rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>${doc.html()}<h3>源码</h3>\n<pre><code class=\"language-typescript\">$source</code></pre>\n<h3>测试用例</h3>\n<pre><code class=\"language-typescript\">$test</code></pre>\n</body>\n<script type=\"text/javascript\" src=\"${
                        Paths.get(
                            "js/prismjs/prism.js"
                        ).toAbsolutePath()
                    }\"></script>\n</html>"
                Utils.writeAndDeleteIfExists(path, html)
                it.get("file://${path.toAbsolutePath()}")
                it.executeScript("document.querySelectorAll('script').forEach(e => e.remove())")
                Files.write(path, it.pageSource.toByteArray())
                shortcuts.add(Shortcut(title, desc, "pages/$title.html"))
            }
        Utils.writeAndDeleteIfExists(pathSet.indexes, Json.encodeToString(shortcuts))
    }
}
