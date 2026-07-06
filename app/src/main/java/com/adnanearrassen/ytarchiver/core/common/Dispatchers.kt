package com.adnanearrassen.ytarchiver.core.common

import javax.inject.Qualifier

/** Qualifiers so ViewModels/repositories can inject the right dispatcher. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/** Application-scoped [kotlinx.coroutines.CoroutineScope] qualifier. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
