package com.nikeboss.keyboard

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class DictionaryHelper(private val context: Context) {

    // Узел нашего дерева
    class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var isWord = false
    }

    private val root = TrieNode()

    fun loadDictionary() {
        try {
            val inputStream = context.assets.open("words_ru.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    val word = line.trim().lowercase()
                    if (word.isNotEmpty()) {
                        insertWord(word)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Добавляем слово в дерево
    private fun insertWord(word: String) {
        var current = root
        for (char in word) {
            if (!current.children.containsKey(char)) {
                current.children[char] = TrieNode()
            }
            current = current.children[char]!!
        }
        current.isWord = true
    }

    // Ищем подсказки
    fun getSuggestions(input: String, maxSuggestions: Int = 5): List<String> {
        if (input.isBlank()) return emptyList()
        val lowerInput = input.lowercase()
        
        // 1. Спускаемся по дереву до последней введенной буквы
        var current = root
        for (char in lowerInput) {
            if (!current.children.containsKey(char)) {
                return emptyList() // Такого слова нет
            }
            current = current.children[char]!!
        }

        // 2. Собираем все слова, которые начинаются с этого куска
        val result = mutableListOf<String>()
        collectWords(current, lowerInput, result, maxSuggestions)
        return result
    }

    private fun collectWords(node: TrieNode, prefix: String, result: MutableList<String>, max: Int) {
        if (result.size >= max) return
        if (node.isWord && prefix != node.toString()) { // Не добавляем само введенное слово
            result.add(prefix)
        }
        for ((char, child) in node.children) {
            collectWords(child, prefix + char, result, max)
            if (result.size >= max) return
        }
    }
}
