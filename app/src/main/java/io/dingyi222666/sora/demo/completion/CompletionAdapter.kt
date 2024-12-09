package io.dingyi222666.sora.demo.completion

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.dingyi222666.sora.demo.R

class CompletionAdapter : RecyclerView.Adapter<CompletionAdapter.ViewHolder>() {

    private var completions: List<String> = emptyList()
    var onItemClick: ((String) -> Unit)? = null

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_completion, parent, false) as TextView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = completions[position]
        val displayText = item.replace("<|>", "")
        holder.textView.text = displayText
        holder.textView.setOnClickListener {
            onItemClick?.invoke(item)
             submitList(emptyList())
        }
    }

    override fun getItemCount() = completions.size

    fun submitList(newCompletions: List<String>) {
        completions = newCompletions
        notifyDataSetChanged()
    }
} 