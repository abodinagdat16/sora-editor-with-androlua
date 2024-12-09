package io.dingyi222666.sora.lua.ai

import io.github.rosemoe.sora.text.Content

data class DiffPatch(
    val patches: List<Patch>,
    val displayText: String
) {
    data class Patch(
        val newText: String
    ) {
        fun apply(editor: Content) {
            val cursor = editor.cursor
            val currentLine = editor.getLine(cursor.leftLine)
            val currentLineContent = currentLine.toString()
            val prefixContent = currentLineContent.substring(0, cursor.leftColumn)
            val suffixContent = currentLineContent.substring(cursor.leftColumn)

            // 计算需要插入的文本
            val insertText = computeInsertText(prefixContent, suffixContent, newText)

            // 如果有需要插入的内容，则执行插入
            if (insertText.isNotEmpty()) {
                editor.insert(
                    cursor.leftLine,
                    cursor.leftColumn,
                    insertText
                )
            }
        }

        private fun computeInsertText(prefix: String, suffix: String, newText: String): String {
            // 移除前后空格以便比较
            val trimmedPrefix = prefix.trim()
            val trimmedSuffix = suffix.trim()
            val trimmedNew = newText.trim()

            // 如果补全内容完全包含在原文中，不需要插入
            if (trimmedPrefix + trimmedSuffix == trimmedNew) {
                return ""
            }

            // 处理分隔符情况（逗号、空格等）
            val lastChar = trimmedPrefix.lastOrNull()
            val firstChar = trimmedSuffix.firstOrNull()
            val separators = setOf(',', ' ', '=', '(', ')', '{', '}')

            return when {
                // 处理逗号分隔的列表
                trimmedNew.startsWith(trimmedPrefix) && trimmedNew.endsWith(trimmedSuffix) -> {
                    val middle = trimmedNew.substring(
                        trimmedPrefix.length,
                        trimmedNew.length - trimmedSuffix.length
                    )
                    if (middle.startsWith(",") && lastChar == ',') {
                        middle.substring(1)
                    } else if (middle.endsWith(",") && firstChar == ',') {
                        middle.substring(0, middle.length - 1)
                    } else {
                        middle
                    }
                }

                // 处理赋值语句
                trimmedPrefix.contains("=") -> {
                    val beforeEquals = trimmedPrefix.substringBefore("=").trim()
                    if (trimmedNew.startsWith(beforeEquals)) {
                        val afterNew = trimmedNew.substringAfter("=").trim()
                        if (trimmedPrefix.endsWith("=")) " $afterNew" else afterNew
                    } else {
                        trimmedNew
                    }
                }

                // 处理其他需要添加空格的情况
                lastChar != null && !separators.contains(lastChar) &&
                trimmedNew.firstOrNull() != null && !separators.contains(trimmedNew.first()) -> {
                    " $trimmedNew"
                }

                // 默认情况
                else -> trimmedNew
            }
        }
    }

    fun apply(editor: Content) {
        editor.beginBatchEdit()
        try {
            patches.forEach { patch ->
                patch.apply(editor)
            }
        } finally {
            editor.endBatchEdit()
        }
    }
}