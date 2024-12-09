package io.dingyi222666.sora.demo.completion

import io.dingyi222666.sora.lua.ai.OpenAICodeCompleter
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AICompletionProvider {

    private val openAICodeCompleter = OpenAICodeCompleter(
        apiKey = "",
        model = "glm-4-flash",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4"
    )

    fun getCompletions(content: Content, cursor: CharPosition): Flow<List<String>> = flow {
        // 获取当前行内容
        val currentLine = content.getLine(cursor.line)
        val beforeCursor = currentLine.substring(0, cursor.column)
        
        val completions = openAICodeCompleter.singleFileCompletion(content, cursor)
            .map { completion ->
                println(completion)
                if (!completion.contains("<|>")) {
                    // 如果补全中没有光标标记，在末尾添加
                    "$completion<|>"
                } else {
                    completion
                }

            }
            .map { completion ->
                if (!completion.startsWith(beforeCursor)) {
                    // 确保补全包含当前行前缀
                    beforeCursor + completion.substringAfter(beforeCursor)
                } else {
                    completion
                }
            }
        
        emit(completions)
    }
} 