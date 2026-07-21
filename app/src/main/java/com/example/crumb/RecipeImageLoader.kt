package com.example.crumb

import android.widget.ImageView
import coil.load
import com.example.crumb.data.RetrofitClient

fun ImageView.loadRecipeImage(imageUrl: String?) {
    load(RetrofitClient.absoluteUrl(imageUrl)) {
        placeholder(R.drawable.recipe_placeholder)
        error(R.drawable.recipe_placeholder)
        fallback(R.drawable.recipe_placeholder)
    }
}
