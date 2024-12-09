package io.dingyi222666.sora.demo.completion

import io.dingyi222666.sora.lua.ai.OpenAICodeCompleter
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.dingyi222666.sora.lua.ai.DiffPatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import android.util.Log

class AICompletionProvider {

    private val openAICodeCompleter = OpenAICodeCompleter(
        apiKey = "",
        model = "glm-4-flash",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4"
    )

    fun getCompletions(content: Content, cursor: CharPosition): Flow<List<DiffPatch>> = flow {
        try {
            val completions = openAICodeCompleter.singleFileCompletion(content, cursor)
            emit(completions)
        } catch (e: Exception) {
            Log.e("AICompletionProvider", "Error getting completions", e)
            emit(emptyList())
        }
    }


} 