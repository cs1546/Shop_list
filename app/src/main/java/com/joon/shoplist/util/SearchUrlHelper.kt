package com.joon.shoplist.util

import java.net.URLEncoder

enum class Store(val label: String) {
    COUPANG("쿠팡"),
    SSG("SSG"),
    COSTCO("코스트코"),
    NAVER("네이버"),
    TRADERS("트레이더스");
}

object SearchUrlHelper {

    fun buildUrl(store: Store, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return when(store) {
            Store.COUPANG -> "https://www.coupang.com/np/search?component=&q=$encoded"
            Store.SSG -> "https://www.ssg.com/search.ssg?query=$encoded"
            Store.COSTCO -> "https://www.costco.co.kr/search?text=$encoded"
            Store.NAVER -> "https://search.shopping.naver.com/search/all?query=$encoded"
            Store.TRADERS -> "https://traders.ssg.com/search.ssg?query=$encoded"
        }
    }
}
