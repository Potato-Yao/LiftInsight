package com.potato.liftinsight.home.route

enum class MainTab {
    Home,
    Body,
    Motion,
    Plan,
    Settings;

    companion object {
        fun fromIndex(index: Int): MainTab {
            return entries.getOrNull(index) ?: Home
        }
    }
}
