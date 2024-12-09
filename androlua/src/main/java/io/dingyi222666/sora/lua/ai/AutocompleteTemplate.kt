package io.dingyi222666.sora.lua.ai

data class AutocompleteTemplate(
    val template: String,
    val stopSequences: List<String>
) {
    companion object {
        fun getTemplateForModel(): AutocompleteTemplate {
            return AutocompleteTemplate(
                template = buildString {
                    appendLine("Given the current code context, fill the code at {FILL_CODE_HERE}. Return only the code without explanation. Use <newline> to indicate a new line.")
                    appendLine("<code>")
                    appendLine("{prefix} {FILL_CODE_HERE} {suffix}")
                    appendLine("</code>")
                    appendLine("fill the code. Don't add any existing code. Your response should only include the code.")
                },
                stopSequences = listOf("```", "\n\n")
            )
        }
    }
}