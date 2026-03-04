package com.t8rin.imagetoolbox.feature.markup_layers.di

import com.t8rin.imagetoolbox.feature.markup_layers.data.AndroidMarkupProjectManager
import com.t8rin.imagetoolbox.feature.markup_layers.domain.MarkupProjectManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface MarkupProjectModule {

    @Binds
    @Singleton
    fun provideMarkupProjectManager(
        manager: AndroidMarkupProjectManager
    ): MarkupProjectManager

}