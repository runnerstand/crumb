package com.example.crumb

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.example.crumb.data.CommunityCommentRepository
import com.example.crumb.data.CommunityCommentRequest
import com.example.crumb.data.CommunityCommentResponse
import com.example.crumb.databinding.DialogCommunityCommentFormBinding
import kotlinx.coroutines.launch

class CommunityCommentFormDialogFragment : DialogFragment() {
    private var _binding: DialogCommunityCommentFormBinding? = null
    private val binding get() = _binding!!
    private val commentRepository = CommunityCommentRepository()
    private val postId: Int by lazy {
        requireArguments().getInt(ARG_POST_ID)
    }
    private val commentId: Int? by lazy {
        arguments?.getInt(ARG_COMMENT_ID)?.takeIf { it > 0 }
    }
    private val existingComment: String by lazy {
        arguments?.getString(ARG_COMMENT_TEXT).orEmpty()
    }
    private val initialRating: Int by lazy {
        arguments?.getInt(ARG_INITIAL_RATING, 0)?.coerceIn(0, 5) ?: 0
    }
    private var selectedRating = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCommunityCommentFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.formTitle.text = if (commentId == null) {
            getString(R.string.add_comment)
        } else {
            getString(R.string.edit_comment)
        }
        binding.commentEditText.filters = arrayOf(InputFilter.LengthFilter(MAX_COMMENT_LENGTH))
        binding.commentEditText.setText(existingComment)
        selectedRating = initialRating
        setupRatingControls()
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        binding.saveButton.setOnClickListener {
            saveComment()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun saveComment() {
        if (!binding.saveButton.isEnabled) {
            return
        }

        binding.formErrorText.visibility = View.GONE
        val request = buildCommentRequest() ?: return
        setSavingState(true)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = commentId?.let {
                commentRepository.updateCommunityComment(it, request)
            } ?: commentRepository.createCommunityComment(postId, request)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = {
                    setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CHANGED to true))
                    dismiss()
                },
                onFailure = { error ->
                    showFormError(error.message ?: getString(R.string.generic_error))
                    setSavingState(false)
                }
            )
        }
    }

    private fun buildCommentRequest(): CommunityCommentRequest? {
        val commentText = binding.commentEditText.text?.toString()?.trim().orEmpty()
        if (commentText.isBlank()) {
            showFormError(getString(R.string.community_comment_required))
            return null
        }
        if (commentText.length > MAX_COMMENT_LENGTH) {
            showFormError(getString(R.string.community_comment_too_long))
            return null
        }

        return CommunityCommentRequest(
            commentText = commentText,
            rating = selectedRating.takeIf { it in 1..5 }
        )
    }

    private fun setupRatingControls() {
        ratingButtons().forEachIndexed { index, button ->
            button.setOnClickListener {
                selectedRating = index + 1
                updateRatingSelection()
            }
        }
        updateRatingSelection()
    }

    private fun updateRatingSelection() {
        ratingButtons().forEachIndexed { index, button ->
            val isSelected = index + 1 <= selectedRating
            button.iconTint = ContextCompat.getColorStateList(
                requireContext(),
                if (isSelected) R.color.primary_orange else R.color.text_muted_brown
            )
            button.isSelected = isSelected
        }
    }

    private fun ratingButtons(): List<MaterialButton> {
        return listOf(
            binding.commentRatingStar1,
            binding.commentRatingStar2,
            binding.commentRatingStar3,
            binding.commentRatingStar4,
            binding.commentRatingStar5
        )
    }

    private fun showFormError(message: String) {
        binding.formErrorText.text = message
        binding.formErrorText.visibility = View.VISIBLE
    }

    private fun setSavingState(isSaving: Boolean) {
        binding.saveButton.isEnabled = !isSaving
        binding.cancelButton.isEnabled = !isSaving
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "community_comment_changed"
        const val RESULT_CHANGED = "changed"
        private const val ARG_POST_ID = "post_id"
        private const val ARG_COMMENT_ID = "comment_id"
        private const val ARG_COMMENT_TEXT = "comment_text"
        private const val ARG_INITIAL_RATING = "initial_rating"
        private const val MAX_COMMENT_LENGTH = 500

        fun newInstance(
            postId: Int,
            comment: CommunityCommentResponse? = null,
            initialRating: Int = 0
        ): CommunityCommentFormDialogFragment {
            return CommunityCommentFormDialogFragment().apply {
                arguments = bundleOf(
                    ARG_POST_ID to postId,
                    ARG_COMMENT_ID to (comment?.id ?: 0),
                    ARG_COMMENT_TEXT to comment?.commentText.orEmpty(),
                    ARG_INITIAL_RATING to initialRating
                )
            }
        }
    }
}
