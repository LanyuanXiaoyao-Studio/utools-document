package com.lanyuanxiaoyao.utools.document

fun main() {
    val list = listOf("1", "2", "3", "4", "5", "6")
    println(Utils.splitList(list) { list, _, last, now ->
        now % 2 == 0
    }.map { it })
}
