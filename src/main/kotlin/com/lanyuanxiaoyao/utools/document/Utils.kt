package com.lanyuanxiaoyao.utools.document

import cn.hutool.crypto.digest.MD5
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.random.Random

data class PathSet(
    val out: Path,
    val pages: Path,
    val indexes: Path,
    val sourceCss: Path,
    val targetCss: Path,
)

@Serializable
data class Shortcut(
    @SerialName("t")
    val title: String,
    @SerialName("d")
    val description: String,
    @SerialName("p")
    val path: String,
)

class Utils {
    companion object {
        fun browser(headless: Boolean = false, proxy: Boolean = false): ChromeDriver {
            System.setProperty(
                ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY,
                "/Users/lanyuanxiaoyao/Downloads/chromedriver_mac64/chromedriver"
            )
            return ChromeDriver(
                ChromeOptions().apply {
                    setBinary("/Users/lanyuanxiaoyao/Downloads/chrome-mac/Chromium.app/Contents/MacOS/Chromium")
                    setHeadless(headless)
                    addArguments(
                        "--disable-gpu",
                        "--no-sandbox",
                        "blink-settings=imagesEnabled=false",
                    )
                    if (proxy)
                        addArguments("--proxy-server=http://127.0.0.1:1080")
                }
            )
        }

        fun browserWithClose(
            headless: Boolean = false,
            proxy: Boolean = false,
            handle: (driver: ChromeDriver) -> Unit
        ) {
            val browser = browser(headless, proxy)
            try {
                handle(browser)
            } finally {
                browser.quit()
            }
        }

        fun httpClient(proxy: Boolean = false): OkHttpClient =
            if (proxy)
                OkHttpClient.Builder().proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(1080))).build()
            else
                OkHttpClient.Builder().build()

        fun downloadText(url: String, proxy: Boolean = false): String {
            val client = httpClient(proxy)
            val request = Request.Builder()
                .url(url)
                .build()
            val response = client.newCall(request).execute()
            return response.body?.string() ?: ""
        }

        fun pathSet(name: String): PathSet {
            val sourceCss = Paths.get("css", "$name.css")
            val out = Paths.get("build", "out", name)
            val pages = Paths.get(out.toString(), "pages")
            val pathSet = PathSet(
                out = out,
                pages = pages,
                indexes = Paths.get(out.toString(), "indexes.json"),
                sourceCss = sourceCss,
                targetCss = Paths.get(pages.toString(), "style.css")
            )
            Files.createDirectories(pathSet.out)
            Files.createDirectories(pathSet.pages)
            return pathSet
        }

        fun writeAndDeleteIfExists(path: Path, text: String) {
            Files.createDirectories(path.parent)
            Files.deleteIfExists(path)
            Files.write(path, text.toByteArray())
        }

        fun readAndEmptyIfNonExists(path: Path) = Files.readAllBytes(path).toString(Charsets.UTF_8)

        fun translate(query: String): String {
            if (query.isBlank()) return ""
            val salt = Random(Instant.now().toEpochMilli()).nextInt(10, 100).toString()
            val appId = "20160128000009605"
            val secret = "UrQhT6doB8Qu_YUArJY7"
            val sign = MD5.create().digestHex("$appId$query$salt$secret")
            val client = httpClient()
            val request = Request.Builder()
                .url("http://api.fanyi.baidu.com/api/trans/vip/translate?q=$query&from=en&to=zh&appid=$appId&salt=$salt&sign=$sign")
                .build()
            val response = client.newCall(request).execute().body
            val result = response?.string() ?: ""
            val map = Json.decodeFromString(JsonObject.serializer(), result)
            val transResult = (map["trans_result"] ?: return "") as JsonArray
            val resultItem = transResult[0] as JsonObject
            return resultItem["dst"].toString()
        }

        fun translateHtml(html: String): String {
            val document = Jsoup.parse(html)
            val elements = document.allElements
                .filterNot { listOf("code", "pre, title").contains(it.tagName()) }
                .filterNot { it.parents().map { p -> p.tagName() }.contains("pre") }
                .filterNot { it.parents().map { p -> p.tagName() }.contains("code") }
                .filter { it.ownText().isNotBlank() }
            val amount = elements.size - 1
            elements.forEachIndexed { index, element ->
                println("Translated: $index/$amount")
                val translateText = translate(element.text())
                when {
                    element.tagName() == "p" -> {
                        element.html("${element.html()}<br><p class=\"trans-p\">$translateText</p>")
                    }
                    element.parents().map { p -> p.tagName() }.any { t -> t.matches(Regex("h\\d")) } -> {
                        element.html("${element.html()}<br><div class=\"trans-p\">$translateText</div>")
                    }
                    else -> {
                        element.html("${element.html()} <span class=\"trans-inline\">$translateText</span>")
                    }
                }
                Thread.sleep(1100)
            }
            return document.html()
        }
    }
}