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
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*
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
            val properties = Properties()
            properties.load(Files.newBufferedReader(Paths.get("browser.properties")))
            val driverPath = (properties["driver"] ?: throw Exception("driverPath not found.")) as String
            val binaryPath = (properties["binary"] ?: throw Exception("binaryPath not found.")) as String
            System.setProperty(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY, driverPath)
            return ChromeDriver(
                ChromeOptions().apply {
                    setBinary(binaryPath)
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

        fun httpClient(proxy: Boolean = false): OkHttpClient {
            val duration = Duration.ofMinutes(5)
            val builder = OkHttpClient.Builder()
                .callTimeout(duration)
                .connectTimeout(duration)
                .readTimeout(duration)
                .writeTimeout(duration)
                .retryOnConnectionFailure(true)
            return if (proxy)
                builder
                    .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(1080)))
                    .build()
            else
                builder.build()
        }

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
                .url("http://api.fanyi.baidu.com/api/trans/vip/translate?q=${URLEncoder.encode(query, Charsets.UTF_8.name())}&from=en&to=zh&appid=$appId&salt=$salt&sign=$sign")
                .build()
            val response = client.newCall(request).execute().body
            val result = response?.string() ?: ""
            println(result)
            val map = Json.decodeFromString(JsonObject.serializer(), result)
            val transResult = (map["trans_result"] ?: return "") as JsonArray
            val resultItem = transResult[0] as JsonObject
            var resultText = resultItem["dst"].toString()
            if (!query.startsWith("\"")) {
                resultText = resultText.replace(Regex("^\""), "")
            }
            if (!query.endsWith("\"")) {
                resultText = resultText.replace(Regex("\"$"), "")
            }
            return resultText
        }

        fun translateHtml(html: String): String {
            val document = Jsoup.parse(html)
            val elements = document.allElements
                .filterNot { listOf("code", "pre, title").contains(it.tagName()) }
                .filterNot { it.parents().map { p -> p.tagName() }.contains("pre") }
                .filterNot { it.parents().map { p -> p.tagName() }.contains("code") }
                .filter { it.ownText().isNotBlank() }
            val amount = elements.size - 1

            val text = elements.joinToString("\n") { it.text() }

            /*elements.forEachIndexed { index, element ->
                print("Translated: $index/$amount, Text: ${element.text()}")
                val translateText = translate(element.text())
                println(" TransText: $translateText")
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
            }*/
            // return document.html()
            return translate(text)
        }
    }
}