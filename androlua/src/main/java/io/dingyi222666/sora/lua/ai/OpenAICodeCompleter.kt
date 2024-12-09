package io.dingyi222666.sora.lua.ai

import android.util.Log
import io.dingyi222666.sora.lua.http.post
import io.dingyi222666.sora.lua.http.postSSE
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import org.json.JSONArray
import org.json.JSONObject

class OpenAICodeCompleter(
    val apiKey: String,
    val model: String,
    val baseUrl: String = "https://api.openai.com/v1"
) : AICodeCompleter {
    override suspend fun singleFileCompletion(
        content: Content,
        position: CharPosition
    ): List<String> {
        val prefix = getCodePrefix(content, position)
        val suffix = getCodeSuffix(content, position)

        val messages = listOf(
            "system" to systemPrompt,
            "user" to buildPrompt(prefix, suffix)
        )

        // 最多重试3次
        repeat(3) {
            try {
                val result = sendRequest(messages)
                    .let { response ->
                        Regex("<option>(.*?)</option>", RegexOption.DOT_MATCHES_ALL)
                            .findAll(response)
                            .map { it.groupValues[1] }
                            .map { it.trim() }
                            .map { it.replace("<newline>", "\n").replace("<\\/newline>", "\n").replace("\\n","\n") }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .take(3)
                            .toList()
                    }

                if (result.isNotEmpty()) {
                    return result
                }
            } catch (e: Exception) {
                Log.e("OpenAICodeCompleter", "Error in completion attempt ${it + 1}", e)
                // 继续下一次重试
            }
        }

        return emptyList()
    }

    private fun buildPrompt(prefix: String, suffix: String) = """
        Complete Lua code.
        <context>$prefix$suffix</context>
        
        IMPORTANT: 
        1. Complete the code naturally
        2. Use <option></option> tags for each completion
        3. Use <newline> for line breaks
        4. Keep completions within 1-5 lines
        
        Output:
    """.trimIndent()

    private val systemPrompt = """You are a code completion assistant.

IMPORTANT RULES:
1. Complete the code from current line
2. Provide 2-3 meaningful completions
3. Use <option> tags to separate completions
4. Use <newline> for line breaks
5. Keep completions within 1-5 lines
6. Keep code concise and focused

Examples:
# Function call
print( ->
<option>print("hello world")</option>
<option>print(table.concat(arr))</option>
<option>print(string.format("%s", value))</option>

# Function definition (short)
function test ->
<option>function test(a, b)<newline>    return a + b<newline>end</option>
<option>function test(tab)<newline>    return #tab<newline>end</option>

# Method call with short callback
table. ->
<option>table.insert(t, value)</option>
<option>table.sort(t, function(a, b)<newline>    return a < b<newline>end)</option>

# Short loop
for i = 1, ->
<option>for i = 1, #arr do<newline>    print(arr[i])<newline>end</option>
<option>for i = 1, count do<newline>    process(i)<newline>end</option>

# Conditional
if value ->
<option>if value then<newline>    return true<newline>end</option>
<option>if value == nil then<newline>    return default<newline>end</option>"""

    // role, content
    suspend fun sendRequest(messages: List<Pair<String, String>>): String {
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { (role, content) ->
                    put(JSONObject().apply {
                        put("role", role)
                        put("content", content)
                    })
                }
            })
            put("temperature", 0)
            put("max_tokens", 300)
            put("n", 1)
           // put("stream", true) // 启用 SSE
        }

        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )

        // 使用 SSE 请求
       /* val responseBuilder = StringBuilder()
        postSSE("$baseUrl/chat/completions", requestBody, headers)
            .collect { event ->
                when {
                    event.data == "[DONE]" -> return@collect
                    event.data.isNotBlank() -> {
                        try {
                            val chunk = JSONObject(event.data)
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("delta")
                                .optString("content", "")
                            
                            if (chunk.isNotBlank()) {
                                responseBuilder.append(chunk)
                            }
                        } catch (e: Exception) {
                            Log.e("OpenAICodeCompleter", "Error parsing SSE chunk", e)
                        }
                    }
                }
            }

        return responseBuilder.toString().trim()*/

        return post("$baseUrl/chat/completions", requestBody, headers)
    }

    // get the lua code prefix
// like
// function quickSort()
// <|>
// end
    private fun getCodePrefix(content: Content, position: CharPosition): String {
        // return the 10 lines code before the cursor
        val beforeText = content.subSequence(0, position.index).toString()
        return beforeText.lines().takeLast(10).joinToString("\n")
    }

    private fun getCodeSuffix(content: Content, position: CharPosition): String {
        // return the 10 lines code after the cursor
        val afterText = content.subSequence(position.index, content.length).toString()
        return afterText.lines().take(10).joinToString("\n")
    }
}

