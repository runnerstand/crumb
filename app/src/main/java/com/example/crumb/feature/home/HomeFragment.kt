package com.example.crumb.feature.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crumb.databinding.DialogCommentsBinding
import com.example.crumb.databinding.FragmentHomeBinding
import com.example.crumb.ui.adapter.CommentAdapter
import com.example.crumb.ui.adapter.CommunityPostAdapter
import com.example.crumb.ui.screens.comments.CommentsViewModel
import com.example.crumb.ui.screens.comments.CommunityCommentUiState
import com.example.crumb.ui.screens.home.CommunityPostUiModel
import com.example.crumb.ui.screens.home.CommunityPostUiState
import com.example.crumb.ui.screens.home.HomeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var postAdapter: CommunityPostAdapter
    private var commentsDialog: Dialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postAdapter = CommunityPostAdapter(
            onOpenComments = ::showCommentsDialog,
            onUpdatePost = viewModel::updatePost,
            onDeletePost = ::confirmDeletePost
        )
        binding.postsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.postsRecyclerView.adapter = postAdapter
        binding.retryButton.setOnClickListener { viewModel.loadPosts() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderPosts) }
                launch {
                    viewModel.operationMessage.collect { message ->
                        message ?: return@collect
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                        viewModel.clearOperationMessage()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPosts()
    }

    private fun renderPosts(state: CommunityPostUiState) = with(binding) {
        progressBar.visibility = if (state is CommunityPostUiState.Loading) View.VISIBLE else View.GONE
        postsRecyclerView.visibility = if (state is CommunityPostUiState.Success) View.VISIBLE else View.GONE
        retryButton.visibility =
            if (state is CommunityPostUiState.Error || state is CommunityPostUiState.Empty) {
                View.VISIBLE
            } else {
                View.GONE
            }
        statusTextView.visibility =
            if (state is CommunityPostUiState.Error || state is CommunityPostUiState.Empty) {
                View.VISIBLE
            } else {
                View.GONE
            }

        when (state) {
            CommunityPostUiState.Loading -> statusTextView.text = ""
            CommunityPostUiState.Empty -> {
                statusTextView.text = "No community posts yet."
                postAdapter.submitList(emptyList())
            }
            is CommunityPostUiState.Error -> {
                statusTextView.text = state.message
                postAdapter.submitList(emptyList())
            }
            is CommunityPostUiState.Success -> postAdapter.submitList(state.posts)
        }
    }

    private fun confirmDeletePost(post: CommunityPostUiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete post?")
            .setMessage("This will also remove its comments.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> viewModel.deletePost(post.id) }
            .show()
    }

    private fun showCommentsDialog(post: CommunityPostUiModel) {
        commentsDialog?.dismiss()
        val dialogBinding = DialogCommentsBinding.inflate(layoutInflater)
        val commentsViewModel = ViewModelProvider(
            this,
            CommentsViewModelFactory(post.id)
        ).get("comments-${post.id}", CommentsViewModel::class.java)
        val adapter = CommentAdapter(
            onUpdateComment = commentsViewModel::updateComment,
            onDeleteComment = { comment ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete comment?")
                    .setMessage("This comment will be removed from the post.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        commentsViewModel.deleteComment(comment.id)
                    }
                    .show()
            }
        )

        dialogBinding.commentsTitleTextView.text = "Comments: ${post.title}"
        dialogBinding.commentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.commentsRecyclerView.adapter = adapter
        dialogBinding.postCommentButton.setOnClickListener {
            commentsViewModel.createComment(dialogBinding.newCommentEditText.text.toString())
            dialogBinding.newCommentEditText.setText("")
        }

        commentsDialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()
            .also {
                it.setOnDismissListener { commentsDialog = null }
                it.show()
            }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    commentsViewModel.uiState.collect { state ->
                        dialogBinding.commentsProgressBar.visibility =
                            if (state is CommunityCommentUiState.Loading) View.VISIBLE else View.GONE
                        dialogBinding.commentsRecyclerView.visibility =
                            if (state is CommunityCommentUiState.Success) View.VISIBLE else View.GONE
                        dialogBinding.commentsStatusTextView.visibility =
                            if (state is CommunityCommentUiState.Error ||
                                state is CommunityCommentUiState.Empty
                            ) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }
                        when (state) {
                            CommunityCommentUiState.Loading -> dialogBinding.commentsStatusTextView.text = ""
                            CommunityCommentUiState.Empty -> {
                                dialogBinding.commentsStatusTextView.text = "No comments yet."
                                adapter.submitList(emptyList())
                            }
                            is CommunityCommentUiState.Error -> {
                                dialogBinding.commentsStatusTextView.text = state.message
                                adapter.submitList(emptyList())
                            }
                            is CommunityCommentUiState.Success -> adapter.submitList(state.comments)
                        }
                    }
                }
                launch {
                    commentsViewModel.operationMessage.collect { message ->
                        message ?: return@collect
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                        commentsViewModel.clearOperationMessage()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        commentsDialog?.dismiss()
        commentsDialog = null
        binding.postsRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }
}

private class CommentsViewModelFactory(
    private val postId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CommentsViewModel(postId = postId) as T
    }
}
