package com.adnanearrassen.ytarchiver.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/** Minimal GitHub Releases API surface used to check for yt-dlp updates. */
interface GithubReleaseApi {

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GithubRelease

    companion object {
        const val BASE_URL = "https://api.github.com/"
        const val YT_DLP_OWNER = "yt-dlp"
        const val YT_DLP_REPO = "yt-dlp"
    }
}

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("assets") val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("size") val size: Long = 0,
)
