/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.stickers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.stickers.StickerFormat
import io.element.android.libraries.matrix.api.stickers.STICKER_PACK_ACCOUNT_DATA_EVENT_TYPE
import io.element.android.libraries.matrix.api.stickers.StickerPackAccountData
import io.element.android.libraries.matrix.api.stickers.StickerPackAsset
import io.element.android.libraries.matrix.api.stickers.StickerPackItem
import io.element.android.libraries.matrix.api.stickers.StickerPackManifest
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Test

class DefaultStickerPackServiceTest {
    @Test
    fun `getPacks returns empty list when account data is missing`() = runTest {
        val service = createStickerPackService(
            matrixClient = FakeMatrixClient(),
            dispatchers = testCoroutineDispatchers(),
        )

        assertThat(service.getPacks()).isEqualTo(Result.success(emptyList<StickerPackManifest>()))
    }

    @Test
    fun `savePacks stores packs in account data`() = runTest {
        val matrixClient = FakeMatrixClient()
        val service = createStickerPackService(
            matrixClient = matrixClient,
            dispatchers = testCoroutineDispatchers(),
        )
        val pack = aStickerPackManifest()

        assertThat(service.savePacks(listOf(pack)).isSuccess).isTrue()
        assertThat(service.getPacks()).isEqualTo(Result.success(listOf(pack)))
    }

    @Test
    fun `importPack loads manifest from url and merges it into account data`() = runTest {
        val pack = aStickerPackManifest()
        val json = DefaultJsonProvider()()
        val matrixClient = FakeMatrixClient(
            getUrlLambda = { Result.success(json.encodeToString(pack).encodeToByteArray()) }
        )
        matrixClient.givenAccountData(
            eventType = STICKER_PACK_ACCOUNT_DATA_EVENT_TYPE,
            content = json.encodeToString(
                StickerPackAccountData(
                    packs = listOf(pack.copy(id = "existing-pack"))
                )
            )
        )
        val service = createStickerPackService(
            matrixClient = matrixClient,
            dispatchers = testCoroutineDispatchers(),
        )

        assertThat(service.importPack("https://example.org/stickers/cats.json")).isEqualTo(Result.success(pack))
        assertThat(service.getPacks().getOrNull()).containsExactly(
            pack.copy(id = "existing-pack"),
            pack,
        )
    }

    private fun createStickerPackService(
        matrixClient: FakeMatrixClient,
        dispatchers: CoroutineDispatchers,
    ): StickerPackService {
        return DefaultStickerPackService(
            context = ApplicationProvider.getApplicationContext<Context>(),
            matrixClient = matrixClient,
            dispatchers = dispatchers,
            jsonProvider = DefaultJsonProvider(),
        )
    }
}

private fun aStickerPackManifest(id: String = "cats-pack") = StickerPackManifest(
    id = id,
    displayName = "Cats",
    description = "Animated and static cat stickers",
    author = "Element X",
    items = listOf(
        StickerPackItem(
            id = "cat-wave",
            displayName = "Cat Wave",
            description = "A waving cat",
            source = StickerPackAsset(
                url = "mxc://matrix.org/cat-wave",
                mimeType = "image/webp",
                format = StickerFormat.STATIC,
                width = 256,
                height = 256,
            ),
            thumbnail = StickerPackAsset(
                url = "mxc://matrix.org/cat-wave-thumb",
                mimeType = "image/webp",
                format = StickerFormat.STATIC,
                width = 96,
                height = 96,
            ),
        )
    ),
)
