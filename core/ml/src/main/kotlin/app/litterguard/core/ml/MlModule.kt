package app.litterguard.core.ml

import app.litterguard.core.domain.ports.ObjectDetectorFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface MlModule {
    @Binds
    fun objectDetectorFactory(implementation: LiteRtObjectDetectorFactory): ObjectDetectorFactory
}
