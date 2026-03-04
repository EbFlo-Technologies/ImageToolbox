package com.t8rin.imagetoolbox.feature.markup_layers.domain.model

sealed class DomainBackgroundBehavior {
    data object None : DomainBackgroundBehavior()
    data object Image : DomainBackgroundBehavior()
    data class Color(
        val width: Int,
        val height: Int,
        val color: Int
    ) : DomainBackgroundBehavior()
}