package com.t8rin.imagetoolbox.feature.markup_layers.domain

import android.net.Uri
import com.t8rin.imagetoolbox.feature.markup_layers.domain.model.DomainBackgroundBehavior

data class ImportedProject(
    val layers: List<MarkupLayer>, // Domain model
    val backgroundBehavior: DomainBackgroundBehavior, // Domain model
    val backgroundUri: Uri
)

interface MarkupProjectManager {
    suspend fun exportProject(
        layers: List<MarkupLayer>, // Domain model
        backgroundBehavior: DomainBackgroundBehavior, // Domain model
        backgroundUri: Uri,
        destinationUri: Uri
    ): Result<Unit>

    suspend fun importProject(
        sourceUri: Uri
    ): Result<ImportedProject>
}