package com.joon.shoplist.util

import java.net.URLEncoder

object SearchUrlHelper {

    fun coupang(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://www.coupang.com/np/search?component=&q=$encoded"
    }

    fun ssg(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://www.ssg.com/search.ssg?query=$encoded"
    }
}
