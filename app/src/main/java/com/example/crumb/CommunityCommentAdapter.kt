package com.example.crumb

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.example.crumb.data.CommunityCommentResponse
import com.example.crumb.databinding.ItemCommunityCommentBinding

class CommunityCommentAdapter(
    private val onEditClick: (CommunityCommentResponse) -> Unit,
    private val onDeleteClick: (CommunityCommentResponse) -> Unit
) : RecyclerView.Adapter<CommunityCommentAdapter.ViewHolder>() {
    private val comments = mutableListOf<CommunityCommentResponse>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemCommunityCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onEditClick,
            onDeleteClick
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size

    fun submitComments(items: List<CommunityCommentResponse>) {
        comments.clear()
        comments.addAll(items)
        notifyDataSetChanged()
    }

    class ViewHolder(
        private val binding: ItemCommunityCommentBinding,
        private val onEditClick: (CommunityCommentResponse) -> Unit,
        private val onDeleteClick: (CommunityCommentResponse) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: CommunityCommentResponse) {
            binding.commentCreatorName.text = comment.creatorName
            binding.commentCreatedAt.text = comment.createdAt
            binding.commentText.text = comment.commentText

            val isOwner = comment.creatorId == LOCAL_USER_ID
            binding.root.setOnClickListener(if (isOwner) {
                View.OnClickListener { showActionsMenu(comment) }
            } else {
                null
            })
        }

        private fun showActionsMenu(comment: CommunityCommentResponse) {
            val popupMenu = PopupMenu(binding.root.context, binding.root)
            popupMenu.menuInflater.inflate(R.menu.comment_actions_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.comment_action_edit -> {
                        onEditClick(comment)
                        true
                    }
                    R.id.comment_action_delete -> {
                        onDeleteClick(comment)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }

    companion object {
        private const val LOCAL_USER_ID = "local-user"
    }
}
