package io.dingyi222666.sora.lua.ai

data class AutocompleteTemplate(
    val template: String,
    val stopSequences: List<String>
) {
    companion object {
        fun getTemplateForModel(): AutocompleteTemplate {
            return AutocompleteTemplate(
                template = buildString {
                    appendLine("Given the current code context, complete the code at {FILL_CODE_HERE}. The first line MUST be the direct completion of the current line. You may optionally add up to 2 additional related lines after it. Return only the code without explanation.")
                    appendLine("<context>")
                    appendLine("{prefix} {FILL_CODE_HERE} {suffix}")
                    appendLine("</context>")
                    appendLine("Your response should only include 1-3 lines of code. The first line MUST complete the current line of code. Do not repeat any existing code.")
                },
                stopSequences = listOf("```", "\n\n\n\n")  // Prevent more than 3 lines
            )
        }
    }
}