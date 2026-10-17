package com.nikeboss.keyboard

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class DictionaryHelper(private val context: Context) {

    private val allWords = mutableListOf<String>()

    fun loadDictionary() {
        try {
            val inputStream = context.assets.open("words_ru.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    val word = line.trim().lowercase()
                    if (word.isNotEmpty()) {
                        allWords.add(word)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSuggestions(input: String, maxSuggestions: Int = 5): List<String> {
        if (input.isBlank()) return emptyList()
        val lowerInput = input.lowercase()
        val result = mutableListOf<String>()

        for (word in allWords) {
            if (word.startsWith(lowerInput) && word != lowerInput) {
                result.add(word)
            }
        }

        if (result.size >= maxSuggestions) {
            return result.take(maxSuggestions)
        }

        if (lowerInput.length >= 2) {
            val typos = mutableListOf<Pair<String, Int>>()
            for (word in allWords) {
                if (word == lowerInput || result.contains(word)) continue
                val distance = levenshteinDistance(lowerInput, word)
                if (distance <= 2) {
                    typos.add(Pair(word, distance))
                }
            }
            typos.sortBy { it.second }
            for (item in typos) {
                if (result.size >= maxSuggestions) break
                result.add(item.first)
            }
        }
        return result
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
