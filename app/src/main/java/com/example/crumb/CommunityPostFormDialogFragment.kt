package com.example.crumb

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.crumb.data.CommunityPostRepository
import com.example.crumb.data.CommunityPostRequest
import com.example.crumb.data.CommunityPostResponse
import com.example.crumb.databinding.DialogCommunityPostFormBinding
import kotlinx.coroutines.launch

class CommunityPostFormDialogFragment : DialogFragment() {
    private var _binding: DialogCommunityPostFormBinding? = null
    private val binding get() = _binding!!
    private val postRepository = CommunityPostRepository()
    private val postId: Int? by lazy {
        arguments?.getInt(ARG_POST_ID)?.takeIf { it > 0 }
    }
    private val existingTitle: String by lazy {
        arguments?.getString(ARG_TITLE).orEmpty()
    }
    private val existingIngredients: List<String> by lazy {
        arguments?.getStringArrayList(ARG_INGREDIENTS).orEmpty()
    }
    private val existingCaption: String by lazy {
        arguments?.getString(ARG_CAPTION).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCommunityPostFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.formTitle.text = if (postId == null) {
            getString(R.string.create_community_post)
        } else {
            getString(R.string.edit_community_post)
        }
        binding.postTitleEditText.filters = arrayOf(InputFilter.LengthFilter(MAX_TITLE_LENGTH))
        binding.postCaptionEditText.filters = arrayOf(InputFilter.LengthFilter(MAX_CAPTION_LENGTH))
        binding.postTitleEditText.setText(existingTitle)
        binding.postIngredientsEditText.setText(existingIngredients.joinToString(", "))
        binding.postCaptionEditText.setText(existingCaption)
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        binding.saveButton.setOnClickListener {
            savePost()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun savePost() {
        if (!binding.saveButton.isEnabled) {
            return
        }

        binding.formErrorText.visibility = View.GONE
        val request = buildPostRequest() ?: return
        setSavingState(true)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = postId?.let {
                postRepository.updateCommunityPost(it, request)
            } ?: postRepository.createCommunityPost(request)
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

    private fun buildPostRequest(): CommunityPostRequest? {
        val title = binding.postTitleEditText.text?.toString()?.trim().orEmpty()
        val ingredients = binding.postIngredientsEditText.text?.toString()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val caption = binding.postCaptionEditText.text?.toString()?.trim().orEmpty()

        if (title.isBlank()) {
            showFormError(getString(R.string.community_post_title_required))
            return null
        }
        if (ingredients.isEmpty()) {
            showFormError(getString(R.string.community_post_ingredients_required))
            return null
        }
        if (caption.isBlank()) {
            showFormError(getString(R.string.community_post_caption_required))
            return null
        }

        return CommunityPostRequest(
            title = title,
            ingredients = ingredients.distinctBy { it.lowercase() },
            caption = caption
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
        const val REQUEST_KEY = "community_post_changed"
        const val RESULT_CHANGED = "changed"
        private const val ARG_POST_ID = "post_id"
        private const val ARG_TITLE = "title"
        private const val ARG_INGREDIENTS = "ingredients"
        private const val ARG_CAPTION = "caption"
        private const val MAX_TITLE_LENGTH = 120
        private const val MAX_CAPTION_LENGTH = 200

        fun newInstance(post: CommunityPostResponse? = null): CommunityPostFormDialogFragment {
            return CommunityPostFormDialogFragment().apply {
                arguments = bundleOf(
                    ARG_POST_ID to (post?.id ?: 0),
                    ARG_TITLE to post?.title.orEmpty(),
                    ARG_INGREDIENTS to ArrayList(post?.ingredientsJson.orEmpty()),
                    ARG_CAPTION to post?.caption.orEmpty()
                )
            }
        }
    }
}
