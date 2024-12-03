package io.dingyi222666.sora.lua

import android.os.Bundle
import android.util.Log
import com.androlua.source.LuaLexer
import com.androlua.source.LuaTokenTypes
import com.androlua.source.LuaParser
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.completion.getCompletionItemComparator
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference

class AndroLuaAutoCompleter(
    private val language: AndroLuaLanguage
) : AutoCompleter {
    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        val keyword = computePrefix(content, position)
        val prefixLength: Int
        val items = tokenize(content, publisher)

        val keywords = keyword.split('.')

        val currentKeyWord = keywords.last()

        prefixLength = currentKeyWord.length

        filterItems(items, keyword, keywords, currentKeyWord, position)

        val completionList = items.map {
            SimpleCompletionItem(it.name, it.description, prefixLength, it.name)
                .kind(it.type)
        }

        publisher.setComparator(getCompletionItemComparator(content, position, completionList))

        publisher.addItems(completionList)

        publisher.updateList()
    }


    private fun filterPackages(
        currentKeyWord: String,
        items: MutableList<CompletionName>,
        keywords: List<String>,
        position: CharPosition
    ) {
        val packageName = keywords.first()

        items.clear()

        if (language.isBasePackage(packageName)) {
            language.getBasePackage(packageName)?.filter {
                it.name.lowercase().startsWith(currentKeyWord)
            }?.let {
                items.addAll(it)
            }
        } else {
            LuaParser.filterJava(packageName, currentKeyWord, position.index)
                .map {
                    CompletionName(it, CompletionItemKind.Function, " :java")
                }.let {
                    items.addAll(it)
                }

        }
    }

    private fun filterItems(
        items: MutableList<CompletionName>,
        keyword: String,
        keywords: List<String>,
        currentKeyWord: String,
        position: CharPosition
    ) {
        Log.d("AndroLuaAutoCompleter", "filterItems: $keyword, $keywords, $currentKeyWord")
        if (keywords.size == 2) {
            filterPackages(currentKeyWord, items, keywords, position)
        } else if (keywords.size == 1) {
            val last = if (keyword.isNotEmpty()) keyword.last() else keyword
            if (last == '.') {
                filterPackages(currentKeyWord, items, keywords, position)
            } else {
                items.addAll(LuaParser.filterLocal(keyword, position.index))

                language.keywords
                    .filter {
                        it.lowercase().indexOf(keyword) == 0
                    }.forEach {
                        items.add(CompletionName(it, CompletionItemKind.Keyword, " :keyword"))
                    }

                language.names
                    .filter {
                        it.lowercase().indexOf(keyword) == 0
                    }.forEach {
                        items.add(CompletionName(it, CompletionItemKind.Function, " :function"))
                    }

                language.packages.keys
                    .filter {
                        it.lowercase().indexOf(keyword) == 0
                    }.forEach {
                        items.add(CompletionName(it, CompletionItemKind.Module, " :package"))
                    }
            }
        }
    }


    private fun computePrefix(
        text: ContentReference,
        position: CharPosition
    ): String {
        val lineContent = text.getLine(position.line)

        // get the first invisible character
        var firstInvisible = -1

        for (i in position.column - 1 downTo 0) {
            if (isInVisibleChar(lineContent[i])) {
                firstInvisible = i
                break
            }
        }

        return if (firstInvisible == -1) {
            lineContent.substring(0, position.column)
        } else {
            lineContent.substring(firstInvisible + 1, position.column)
        }
    }


    private fun isInVisibleChar(c: Char): Boolean {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n'
    }

    private fun tokenize(
        content: ContentReference,
        publisher: CompletionPublisher
    ): MutableList<CompletionName> {
        val maxRow = 9999
        val items = mutableSetOf<CompletionName>()

        val contentString = content.toString()


        if (!LuaParser.lexer(
                contentString,
                publisher
            )
        ) {
            return mutableListOf()
        }


        val lexer = LuaLexer(contentString)

        // start
        var idx = 0

        var lastType: LuaTokenTypes? = null

        var lastName = ""

        var bul = StringBuilder()
        var isModule = false

        while (true) {
            publisher.checkCancelled();

            val type = lexer.advance() ?: break
            val len = lexer.yylength()
            idx += len

            // check module ?
            if (isModule && lastType == LuaTokenTypes.STRING && type != LuaTokenTypes.STRING) {
                val mod = bul.toString()
                if (bul.length > 2) {
                    val m = mod.substring(1, mod.length - 1)
                    val ms =
                        m.split("[.$]".toRegex()).dropLastWhile { it.isEmpty() }
                            .filter { it != "*" }
                    val na = ms[ms.size - 1]
                    items.add(CompletionName(na, CompletionItemKind.Module, " :import"))
                    LuaParser.addUserWord("$na :import")
                }
                bul = java.lang.StringBuilder()
                isModule = false
            }

            when (type) {
                LuaTokenTypes.STRING, LuaTokenTypes.LONG_STRING -> {
                    //字符串
                    if (lastName == "require" || lastName == "import") isModule = true

                    if (isModule) bul.append(lexer.yytext())
                }

                LuaTokenTypes.NAME -> {

                    val name = lexer.yytext()
                    if (lastType == LuaTokenTypes.FUNCTION) {
                        //函数名
                        items.add(
                            CompletionName(
                                name,
                                CompletionItemKind.Function,
                                " :function"
                            )
                        )
                    } /*else if (language.isUserWord(name)) {
                        tokens.add(Pair(len, Lexer.LITERAL))
                    } else if (lastType == LuaTokenTypes.GOTO || lastType == LuaTokenTypes.AT) {
                        tokens.add(Pair(len, Lexer.LITERAL))
                    } else if (lastType == LuaTokenTypes.MULT && lastType3 == LuaTokenTypes.LOCAL) {
                        tokens.add(Pair(len, Lexer.OPERATOR))
                    } else if (language.isBasePackage(name)) {
                        tokens.add(Pair(len, Lexer.NAME))
                    } else if (lastType == LuaTokenTypes.DOT && language.isBasePackage(lastName) && language.isBaseWord(
                            lastName,
                            name
                        )
                    ) {
                        //标准库函数
                        tokens.add(Pair(len, Lexer.NAME))
                    } else if (language.isName(name)) {
                        tokens.add(Pair(len, Lexer.NAME))
                    } else {
                        tokens.add(Pair(len, Lexer.NORMAL))
                    }*/

                    /*  if (lastType != LuaTokenTypes.DOT) {
                          val loc = false

                          if (!loc && values.containsKey(name)) {
                              val ls = values[name]
                              for (l in ls!!) {
                                  if (l.first == idx) {
                                      val p: Pair = tokens.get(tokens.size - 1)
                                      val tp = l.second
                                      if (tp == LexState.VVOID) {
                                          if (p.second == Lexer.NORMAL) p.second =
                                              Lexer.GLOBAL
                                          break
                                      } else if (tp == LexState.VUPVAL) {
                                          p.second = Lexer.UPVAL
                                          break
                                      } else if (tp == LexState.VLOCAL) {
                                          p.second = Lexer.LOCAL
                                          break
                                      }
                                  }
                              }
                          }
                      }*/

                    if (lastType == LuaTokenTypes.ASSIGN && name == "require") {
                        items.add(CompletionName(name, CompletionItemKind.Module, " :require"))
                        /* if (lastNameIdx >= 0) {
                             val p: Pair = tokens.get(lastNameIdx - 1)
                             p.second = Lexer.LITERAL
                             lastNameIdx = -1
                         }*/
                    }
                    lastName = name
                }

                else -> {}
            }
            if (type != LuaTokenTypes.WHITE_SPACE
            ) {
                lastType = type
            }

        }

        return items.toMutableList()
    }
}