package com.lanyuanxiaoyao.utools.document

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main() {
    println(Json.encodeToString(Shortcut("1", "2", "3")))
}