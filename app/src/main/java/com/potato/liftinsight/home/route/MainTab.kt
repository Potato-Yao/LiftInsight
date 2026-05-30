package com.potato.liftinsight.home.route

enum class MainTab {
    Home,
    Record,
    Motion,
    Plan,
    Settings;

    companion object {
        fun fromIndex(index: Int): MainTab {
            return entries.getOrNull(index) ?: Home
        }
    }
}
