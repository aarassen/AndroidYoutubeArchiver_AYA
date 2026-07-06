package com.adnanearrassen.ytarchiver.python

import com.adnanearrassen.ytarchiver.domain.model.MediaInfo
import com.adnanearrassen.ytarchiver.domain.model.OpResult
import com.adnanearrassen.ytarchiver.domain.repository.MediaAnalyzer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtDlpMediaAnalyzer @Inject constructor(
    private val service: YtDlpService,
) : MediaAnalyzer {
    override suspend fun analyze(url: String): OpResult<MediaInfo> = service.analyze(url)
}
