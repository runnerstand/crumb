package com.example.crumb

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.data.CommunityCommentRepository
import com.example.crumb.data.CommunityCommentResponse
import com.example.crumb.databinding.DialogCommunityCommentsBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CommunityCommentsDialogFragment : DialogFragment() {
    private var _binding: DialogCommunityCommentsBinding? = null
    private val binding get() = _binding!!
    private val commentRepository = CommunityCommentRepository()
    private val commentAdapter = CommunityCommentAdapter(
        onEditClick = ::editComment,
        onDeleteClick = ::confirmDeleteComment
    )
    private val postId: Int by lazy {
        requireArguments().getInt(ARG_POST_ID)
    }
    private val postTitle: String by lazy {
        requireArguments().getString(ARG_POST_TITLE).orEmpty()
    }
    private var loadCommentsJob: Job? = null
    private var deleteCommentJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCommunityCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.commentsTitle.text = if (postTitle.isBlank()) {
            getString(R.string.community_comments_title)
        } else {
            getString(R.string.community_comments_title) + ": " + postTitle
        }
        binding.commentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.commentsRecyclerView.adapter = commentAdapter
        binding.commentRetryButton.setOnClickListener {
            loadComments()
        }
        binding.addCommentButton.setOnClickListener {
            CommunityCommentFormDialogFragment.newInstance(postId)
                .show(childFragmentManager, "CommunityCommentFormDialog")
        }
        childFragmentManager.setFragmentResultListener(
            CommunityCommentFormDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            loadComments()
        }
        loadComments()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun loadComments() {
        loadCommentsJob?.cancel()
        showLoading()

        loadCommentsJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = commentRepository.getCommunityComments(postId)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { comments ->
                    if (comments.isEmpty()) {
                        showEmpty()
                    } else {
                        showComments(comments)
                    }
                },
                onFailure = { error ->
                    showError(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.community_comments_error)
                    )
                }
            )
        }
    }

    private fun showLoading() {
        binding.commentStatusText.visibility = View.VISIBLE
        binding.commentStatusText.text = getString(R.string.community_comments_loading)
        binding.commentRetryButton.visibility = View.GONE
        binding.commentRetryButton.isEnabled = false
        binding.commentsRecyclerView.visibility = View.GONE
    }

    private fun showEmpty() {
        commentAdapter.submitComments(emptyList())
        binding.commentStatusText.visibility = View.VISIBLE
        binding.commentStatusText.text = getString(R.string.community_comments_empty)
        binding.commentRetryButton.visibility = View.GONE
        binding.commentRetryButton.isEnabled = true
        binding.commentsRecyclerView.visibility = View.GONE
    }

    private fun showError(message: String) {
        commentAdapter.submitComments(emptyList())
        binding.commentStatusText.visibility = View.VISIBLE
        binding.commentStatusText.text = message
        binding.commentRetryButton.visibility = View.VISIBLE
        binding.commentRetryButton.isEnabled = true
        binding.commentsRecyclerView.visibility = View.GONE
    }

    private fun showComments(comments: List<CommunityCommentResponse>) {
        commentAdapter.submitComments(comments)
        binding.commentStatusText.visibility = View.GONE
        binding.commentRetryButton.visibility = View.GONE
        binding.commentRetryButton.isEnabled = true
        binding.commentsRecyclerView.visibility = View.VISIBLE
    }

    private fun editComment(comment: CommunityCommentResponse) {
        CommunityCommentFormDialogFragment.newInstance(postId, comment)
            .show(childFragmentManager, "CommunityCommentFormDialog")
    }

    private fun confirmDeleteComment(comment: CommunityCommentResponse) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_community_comment_title)
            .setMessage(R.string.delete_community_comment_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteComment(comment)
            }
            .show()
    }

    private fun deleteComment(comment: CommunityCommentResponse) {
        if (deleteCommentJob?.isActive == true) {
            return
        }

        binding.commentRetryButton.isEnabled = false
        deleteCommentJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = commentRepository.deleteCommunityComment(comment.id)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { loadComments() },
                onFailure = { error ->
                    showError(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.generic_error)
                    )
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadCommentsJob?.cancel()
        deleteCommentJob?.cancel()
        binding.commentsRecyclerView.adapter = null
        _binding = null
    }

    companion object {
        private const val ARG_POST_ID = "post_id"
        private const val ARG_POST_TITLE = "post_title"

        fun newInstance(postId: Int, postTitle: String): CommunityCommentsDialogFragment {
            return CommunityCommentsDialogFragment().apply {
                arguments = bundleOf(
                    ARG_POST_ID to postId,
                    ARG_POST_TITLE to postTitle
                )
            }
        }
    }
}
