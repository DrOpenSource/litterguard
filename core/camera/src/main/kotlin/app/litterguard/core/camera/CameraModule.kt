package app.litterguard.core.camera

import app.litterguard.core.domain.ports.FrameSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface CameraModule {
    @Binds
    fun frameSource(implementation: CameraXFrameSource): FrameSource
}
