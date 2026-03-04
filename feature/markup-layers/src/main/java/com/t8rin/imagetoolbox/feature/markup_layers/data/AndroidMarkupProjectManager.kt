/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2024 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package com.t8rin.imagetoolbox.feature.markup_layers.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import com.t8rin.imagetoolbox.core.domain.coroutines.DispatchersHolder
import com.t8rin.imagetoolbox.core.domain.image.ImageGetter
import com.t8rin.imagetoolbox.core.domain.json.JsonParser
import com.t8rin.imagetoolbox.feature.markup_layers.data.model.BackgroundBehaviorDto
import com.t8rin.imagetoolbox.feature.markup_layers.data.model.BackgroundDtoType
import com.t8rin.imagetoolbox.feature.markup_layers.data.model.LayerDtoType
import com.t8rin.imagetoolbox.feature.markup_layers.data.model.MarkupProjectDto
import com.t8rin.imagetoolbox.feature.markup_layers.data.model.toDomainLayer
import com.t8rin.imagetoolbox.feature.markup_layers.data.model.toDto
import com.t8rin.imagetoolbox.feature.markup_layers.domain.ImportedProject
import com.t8rin.imagetoolbox.feature.markup_layers.domain.LayerType
import com.t8rin.imagetoolbox.feature.markup_layers.domain.MarkupLayer
import com.t8rin.imagetoolbox.feature.markup_layers.domain.MarkupProjectManager
import com.t8rin.imagetoolbox.feature.markup_layers.domain.model.DomainBackgroundBehavior
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

internal class AndroidMarkupProjectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageGetter: ImageGetter<Bitmap>,
    private val jsonParser: JsonParser,
    dispatchersHolder: DispatchersHolder
) : MarkupProjectManager, DispatchersHolder by dispatchersHolder {

    override suspend fun exportProject(
        layers: List<MarkupLayer>,
        backgroundBehavior: DomainBackgroundBehavior,
        backgroundUri: Uri,
        destinationUri: Uri
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val assets = mutableMapOf<String, Bitmap>()
            var assetCounter = 0

            // 1. Map layers to DTOs and extract image bitmaps
            val dtos = layers.map { layer ->
                if (layer.type is LayerType.Picture.Image) {
                    val assetName = "assets/image_${assetCounter++}.png"

                    // Grab the actual bitmap data from memory/URI
                    val bitmap = imageGetter.getImage(layer.type.imageData)
                    if (bitmap != null) {
                        assets[assetName] = bitmap
                    }

                    layer.toDto(assetName)
                } else {
                    layer.toDto(null) // Text and Stickers serialize themselves natively
                }
            }

            // 2. Map Background to DTO and extract its bitmap if it's an image
            val backgroundDto = when (backgroundBehavior) {
                is DomainBackgroundBehavior.None -> BackgroundBehaviorDto(type = BackgroundDtoType.NONE)
                is DomainBackgroundBehavior.Color -> BackgroundBehaviorDto(
                    type = BackgroundDtoType.COLOR,
                    width = backgroundBehavior.width,
                    height = backgroundBehavior.height,
                    color = backgroundBehavior.color
                )
                is DomainBackgroundBehavior.Image -> {
                    val bgAssetName = "assets/background.png"

                    // Grab the background image bitmap from memory/URI
                    val bgBitmap = imageGetter.getImage(backgroundUri.toString())
                    if (bgBitmap != null) {
                        assets[bgAssetName] = bgBitmap.image
                    }

                    BackgroundBehaviorDto(
                        type = BackgroundDtoType.IMAGE,
                        assetName = bgAssetName
                    )
                }
            }

            // 3. Create the Project DTO WITH the background!
            val projectDto = MarkupProjectDto(
                version = 1,
                background = backgroundDto, // <-- This is the missing piece!
                layers = dtos
            )

            val jsonString = jsonParser.toJson(
                obj = projectDto,
                type = MarkupProjectDto::class.java
            ) ?: throw IllegalStateException("Failed to serialize project to JSON")

            // 4. Open the destination file and start zipping
            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipOut ->

                    // Write the layout.json manifest
                    zipOut.putNextEntry(ZipEntry("layout.json"))
                    zipOut.write(jsonString.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()

                    // Write each image bitmap into the zip's assets folder
                    assets.forEach { (name, bitmap) ->
                        zipOut.putNextEntry(ZipEntry(name))
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, zipOut)
                        zipOut.closeEntry()
                    }
                }
            } ?: throw IllegalStateException("Could not open output stream for URI: $destinationUri")
        }
    }
    override suspend fun importProject(
        sourceUri: Uri
    ): Result<ImportedProject> = withContext(ioDispatcher) {
        runCatching {
            // Create a unique temporary folder in the app cache to hold unzipped files
            val projectDir = File(context.cacheDir, "itp_projects/${System.currentTimeMillis()}")
            projectDir.mkdirs()

            var layoutJson: String? = null

            // 1. Unzip the file
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == "layout.json") {
                            layoutJson = zipIn.readBytes().toString(Charsets.UTF_8)
                        } else if (entry.name.startsWith("assets/")) {
                            val assetFile = File(projectDir, entry.name.removePrefix("assets/"))
                            assetFile.parentFile?.mkdirs()
                            assetFile.outputStream().use { fileOut ->
                                zipIn.copyTo(fileOut)
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            } ?: throw IllegalStateException("Could not open input stream for URI: $sourceUri")

            val json = layoutJson ?: throw IllegalStateException("layout.json missing in project file")

            // 2. Decode the DTO
            val projectDto = jsonParser.fromJson<MarkupProjectDto>(
                json = json,
                type = MarkupProjectDto::class.java
            ) ?: throw IllegalArgumentException("Failed to parse project JSON data")

            // 3. Map Layers (Data DTO -> Domain Model)
            val restoredLayers = projectDto.layers.map { layerDto ->
                if (layerDto.type.type == LayerDtoType.IMAGE) { // Check the enum type!
                    // Reattach the unzipped file URI to the image layer
                    val assetName = layerDto.type.assetName ?: ""
                    val assetFile = File(projectDir, assetName.removePrefix("assets/"))
                    layerDto.toDomainLayer(assetFile.toUri().toString())
                } else {
                    layerDto.toDomainLayer(null)
                }
            }

            // 4. Map Background (Data DTO -> Domain Model)
            var bgBehavior: DomainBackgroundBehavior = DomainBackgroundBehavior.None
            var bgUri = Uri.EMPTY

            when (projectDto.background.type) {
                BackgroundDtoType.NONE -> {
                    bgBehavior = DomainBackgroundBehavior.None
                }
                BackgroundDtoType.COLOR -> {
                    bgBehavior = DomainBackgroundBehavior.Color(
                        width = projectDto.background.width ?: 1,
                        height = projectDto.background.height ?: 1,
                        color = projectDto.background.color ?: 0
                    )
                }
                BackgroundDtoType.IMAGE -> {
                    bgBehavior = DomainBackgroundBehavior.Image
                    val bgAssetName = projectDto.background.assetName ?: "assets/background.png"
                    val assetFile = File(projectDir, bgAssetName.removePrefix("assets/"))
                    bgUri = assetFile.toUri()
                }
            }

            // 5. Bundle everything into the Domain object and return it!
            // Because this is the last line of `runCatching`, it automatically gets wrapped in Result.success()
            ImportedProject(
                layers = restoredLayers,
                backgroundBehavior = bgBehavior,
                backgroundUri = bgUri
            )
        }
    }
}