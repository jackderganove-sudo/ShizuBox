package de.emil.shizubox.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** A video card in search results or the feed. */
data class VideoItem(
    val url: String,
    val title: String,
    val channel: String?,
    val channelUrl: String?,
    val durationSec: Long,
    val thumbnail: String?,
    val uploaded: Long,
)

/** A channel found by the search. */
data class ChannelItem(val url: String, val name: String, val avatar: String?, val subscribers: Long)

/**
 * Search, trends and channel feeds through NewPipe Extractor – no Google account, no API key.
 */
object Discover {
    @Volatile private var ready = false

    private object Http : Downloader() {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        override fun execute(request: Request): Response {
            val conn = URL(request.url()).openConnection() as HttpURLConnection
            conn.requestMethod = request.httpMethod()
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            request.headers().forEach { (k, values) -> values.forEach { conn.addRequestProperty(k, it) } }
            request.dataToSend()?.let { body ->
                conn.doOutput = true
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val headers = conn.headerFields.filterKeys { it != null }
            return Response(code, conn.responseMessage, headers, text, conn.url.toString())
        }
    }

    private fun ensure() {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val l = Locale.getDefault()
            NewPipe.init(Http, Localization(l.language, l.country.ifEmpty { null }), ContentCountry(l.country.ifEmpty { "DE" }))
            ready = true
        }
    }

    private fun List<Image>.best() = maxByOrNull { it.width * it.height.coerceAtLeast(1) }?.url ?: firstOrNull()?.url

    private fun StreamInfoItem.toItem() = VideoItem(
        url = url,
        title = name,
        channel = uploaderName,
        channelUrl = uploaderUrl,
        durationSec = duration.coerceAtLeast(0),
        thumbnail = thumbnails.best(),
        uploaded = runCatching { uploadDate?.instant?.toEpochMilli() }.getOrNull() ?: 0,
    )

    data class SearchResult(val videos: List<VideoItem>, val channels: List<ChannelItem>)

    suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        ensure()
        val yt = ServiceList.YouTube
        val info = SearchInfo.getInfo(yt, yt.searchQHFactory.fromQuery(query))
        val items: List<InfoItem> = info.relatedItems
        SearchResult(
            videos = items.filterIsInstance<StreamInfoItem>().map { it.toItem() },
            channels = items.filterIsInstance<ChannelInfoItem>().map { ChannelItem(it.url, it.name, it.thumbnails.best(), it.subscriberCount) },
        )
    }

    /** YouTube's default kiosk (trends / what YouTube currently shows as popular). */
    suspend fun trending(): List<VideoItem> = withContext(Dispatchers.IO) {
        ensure()
        val kiosk = ServiceList.YouTube.kioskList.defaultKioskExtractor
        kiosk.fetchPage()
        kiosk.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toItem() }
    }

    suspend fun channel(url: String): Subscription = withContext(Dispatchers.IO) {
        ensure()
        val info = ChannelInfo.getInfo(url)
        Subscription(info.url, info.name, info.avatars.best())
    }

    /** Latest videos of the followed channels (RSS-based feed, fast), newest first. */
    suspend fun feed(subs: List<Subscription>): List<VideoItem> = withContext(Dispatchers.IO) {
        ensure()
        coroutineScope {
            subs.map { s ->
                async {
                    runCatching { FeedInfo.getInfo(s.url).relatedItems.map { it.toItem().copy(channel = it.uploaderName ?: s.name) } }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten().sortedByDescending { it.uploaded }
        }
    }
}
