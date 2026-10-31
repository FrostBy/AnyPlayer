package me.romanovsky.anyplayer.media

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.awt.Color
import java.awt.image.BufferedImage
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.JPanel
import javax.swing.SwingUtilities
import me.romanovsky.anyplayer.analysis.SpectrumSession
import me.romanovsky.anyplayer.analysis.WaveformAnalysis
import me.romanovsky.anyplayer.images.AnimationDecoder
import me.romanovsky.anyplayer.images.AnimationLoops
import me.romanovsky.anyplayer.images.AnimationTimeline
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.InputEvents
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat

class NativeMediaTest {
    @Test fun `streaming waveform renders low rate zoom and long input without pcm retention`() {
        val path = temporary.root.toPath().resolve("long-low-rate.wav")
        ToolProcess.capture(tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=8000:duration=120", path.toString()))
        val source = MediaSource.parse(path.toString())
        val info = MediaProbe.inspect(tool("ffprobe"), source)
        val full = WaveformAnalysis.render(tool("ffmpeg"), source, info.audio.single(), info.analysisOrigin, 0.0, 120.0, false)
        val zoom = WaveformAnalysis.render(tool("ffmpeg"), source, info.audio.single(), info.analysisOrigin, 1.0, 1.1, false)
        assertEquals(1600, full.width)
        assertTrue(zoom.width in 800..801)
        assertTrue((0 until zoom.width).any { zoom.getRGB(it, 200) and 0xFFFFFF != 0 })
    }

    @Test fun `stream reader failure and cancellation terminate only the owned process`() {
        val arguments = listOf("-v", "error", "-nostdin", "-re", "-f", "lavfi", "-i", "anullsrc=r=8000:cl=mono", "-f", "s16le", "pipe:1")
        ToolProcess(tool("ffmpeg"), arguments).use { owned ->
            val started = System.nanoTime()
            assertThrows(java.util.concurrent.ExecutionException::class.java) { owned.readOutput<Unit>(10) { throw java.io.IOException("fixture reader failure") } }
            assertFalse(owned.process.isAlive)
            assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(3))
        }
        ToolProcess(tool("ffmpeg"), arguments).use { owned ->
            val entered = java.util.concurrent.CountDownLatch(1)
            val task = java.util.concurrent.FutureTask {
                owned.readOutput(10) { input -> entered.countDown(); val buffer = ByteArray(4096); while (input.read(buffer) >= 0) { if (Thread.currentThread().isInterrupted) break } }
            }
            Thread.ofVirtual().start(task)
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            task.cancel(true)
            await { !owned.process.isAlive }
        }
    }

    @Test fun `local preview prepares both sides and reuses refined neighbors without a storyboard`() {
        val source = MediaSource.parse(fixture.toString())
        val info = MediaProbe.inspect(tool("ffprobe"), source)
        LocalPreviewWindow(tool("ffmpeg"), source, info.video!!, info.analysisOrigin, 10.0).use { window ->
            val started = System.nanoTime()
            window.focus(5.0)
            await { window.failure?.let { throw AssertionError(it) }; window.nearest(5.0) != null }
            val firstMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            await { window.failure?.let { throw AssertionError(it) }; listOf(3.1, 4.9, 5.0, 5.1, 6.9).all { window.refined(it) != null } }
            println("Local preview: first=${firstMillis}ms, neighborhood=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)}ms")
            val before = window.refined(5.1)!!.image
            repeat(50) { window.focus(5.1) }
            assertSame(before, window.refined(5.1)!!.image)
            window.focus(9.0)
            window.focus(1.0)
            await { window.failure?.let { throw AssertionError(it) }; window.refined(.8) != null && window.refined(1.2) != null }
            assertNotNull(window.refined(5.0))
        }
    }

    @Test fun `preview storyboard is prepared once and serves warmed frames without a decoder`() {
        val source = MediaSource.parse(fixture.toString())
        val info = MediaProbe.inspect(tool("ffprobe"), source)
        val cache = temporary.newFolder("storyboards").toPath()
        val index = PreviewStoryboard(tool("ffmpeg"), source, info.video!!.index, info.analysisOrigin, 10.0, cache)
        var warm: PreviewStoryboard? = null
        try {
            await { index.failure?.let { throw AssertionError(it) }; index.readyCount > 0 }
            val cached = PreviewStoryboard(cache.resolve("missing-ffmpeg.exe"), source, info.video!!.index, info.analysisOrigin, 10.0, cache)
            warm = cached
            await { index.failure?.let { throw AssertionError(it) }; index.readyCount == 20 }
            assertNotNull(index.peek(1.0))
            assertNotNull(index.peek(9.9))
            val start = System.nanoTime()
            repeat(10_000) { assertNotNull(index.peek(it % 10.0)) }
            assertTrue("Warm lookups must not spawn or wait for FFmpeg", System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1))
            await { cached.failure?.let { throw AssertionError(it) }; cached.readyCount == 20 }
            assertNotNull(cached.peek(8.0))
        } finally { index.close(); warm?.close() }
    }

    @Test fun `timeline thumbnails decode the requested source position without touching playback`() {
        val source = MediaSource.parse(fixture.toString())
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fixture))
        val info = MediaProbe.inspect(tool("ffprobe"), source)
        val first = MediaThumbnail.read(tool("ffmpeg"), source, info.video!!.index, info.analysisOrigin, 1.0)
        val later = MediaThumbnail.read(tool("ffmpeg"), source, info.video!!.index, info.analysisOrigin, 8.0)
        assertTrue(first.width <= 320 && first.height <= 180)
        assertTrue((0 until first.width step 8).any { x -> (0 until first.height step 8).any { y -> first.getRGB(x, y) != later.getRGB(x, y) } })
        assertArrayEquals(fingerprint, java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source.localPath!!)))
    }

    @Test fun `video frame inspection returns actual neighboring timestamps in a bounded window`() {
        val source = MediaSource.parse(fixture.toString())
        val info = MediaProbe.inspect(tool("ffprobe"), source)
        val window = MediaProbe.videoFrames(tool("ffprobe"), source, info.video!!.index, info.analysisOrigin, 2.0)
        assertTrue(window.times.any { it < 2.0 })
        assertTrue(window.times.any { it > 2.0 })
        assertEquals(window.times.distinct().sorted(), window.times)
        assertEquals(64, window.times.count { it > 2.0005 })
        assertFalse(window.atEnd)
        val tail = MediaProbe.videoFrames(tool("ffprobe"), source, info.video!!.index, info.analysisOrigin, 9.9)
        assertTrue(tail.atEnd)
        assertTrue(tail.times.last() < 10.0)
    }

    @Test fun `waveform isolates stereo channels and regenerates the selected time range`() {
        val path = temporary.root.toPath().resolve("stereo.wav")
        ToolProcess.capture(tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i",
            "aevalsrc=0.8*sin(2*PI*440*t)*between(t\\,1\\,2)|0:s=48000:d=3", path.toString()))
        val source = MediaSource.parse(path.toString())
        val info = MediaProbe.inspect(tool("ffprobe"), source)
        val stream = info.audio.single()
        assertEquals(2, stream.channels)
        val full = WaveformAnalysis.render(tool("ffmpeg"), source, stream, info.analysisOrigin, 0.0, 3.0, true)
        val zoomed = WaveformAnalysis.render(tool("ffmpeg"), source, stream, info.analysisOrigin, 1.2, 1.8, true)
        fun energy(image: BufferedImage, x: Int, from: Int, until: Int) = (from until until).sumOf { y ->
            val rgb = image.getRGB(x, y)
            ((rgb shr 16 and 255) + (rgb shr 8 and 255) + (rgb and 255)).toLong()
        }
        assertEquals(1600, full.width)
        assertEquals(400, full.height)
        assertTrue(energy(full, 800, 0, 200) > 10 * energy(full, 800, 200, 400))
        assertTrue(energy(zoomed, 200, 0, 200) > 10 * energy(full, 200, 0, 200))
    }

    @Test fun `nonzero container start and delayed audio share the native source timeline`() {
        val path = temporary.root.toPath().resolve("offset.mp4")
        ToolProcess.capture(tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "testsrc2=size=160x90:rate=12:duration=4", "-itsoffset", "1", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000:duration=4", "-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", "-output_ts_offset", "5", path.toString()))
        val source = MediaSource.parse(path.toString())
        val info = MediaProbe.inspect(tool("ffprobe"), source)
        NativeRuntime.loadVlc(root.resolve(if (windows) "vlc" else "vlc/lib"))
        val factory = MediaPlayerFactory(null, listOf("--aout=dummy", "--quiet"))
        lateinit var component: CallbackMediaPlayerComponent
        SwingUtilities.invokeAndWait { component = CallbackMediaPlayerComponent(factory, null, InputEvents.NONE, true, null) }
        val player = component.mediaPlayer()
        try {
            assertTrue(player.media().startPaused(source.argument))
            println("Offset fixture: formatStart=${info.start}, duration=${info.duration}, audioStart=${info.audio.first().start}, VLC time=${player.status().time()}, length=${player.status().length()}")
            println("Offset phase: before seek")
            player.controls().setTime(2000)
            println("Offset phase: after seek")
            await { kotlin.math.abs(player.status().time() - 2000) < 200 }
            println("Offset phase: seek confirmed")
            val failure = AtomicReference<String>()
            val analysis = SpectrumSession(tool("ffmpeg"), source, info.audio.first(), info.analysisOrigin, 2.0, failed = failure::set)
            try {
                println("Offset phase: waiting for live frame")
                await { failure.get()?.let { throw AssertionError(it) }; analysis.frameAt(2.0) != null }
                println("Offset phase: live frame ready")
                assertTrue(kotlin.math.abs(analysis.frameAt(2.0)!!.time - player.status().time() / 1000.0) <= .2)
                val silent = analysis.frameAt(2.0)!!.image
                val overview = SpectrumSession.overview(tool("ffmpeg"), source, info.audio.first(), info.analysisOrigin, player.status().length() / 1000.0)
                println("Offset phase: overview ready")
                fun energy(image: BufferedImage, x: Int): Long = (image.height - 20 until image.height).sumOf { y -> val rgb = image.getRGB(x, y); ((rgb shr 16 and 255) + (rgb shr 8 and 255) + (rgb and 255)).toLong() }
                val liveSilence = energy(silent, silent.width - 1)
                val overviewSilence = energy(overview, (overview.width * .2).toInt())
                val overviewTone = energy(overview, (overview.width * .7).toInt())
                println("Offset signal energy: live=$liveSilence, overview silence=$overviewSilence, overview tone=$overviewTone")
                assertTrue("The first two source seconds must be silence, not audio from the future", liveSilence < 100)
                assertTrue("Full-file overview must keep the leading empty edit", overviewSilence < overviewTone)
                val waveform = WaveformAnalysis.render(tool("ffmpeg"), source, info.audio.first(), info.analysisOrigin, 0.0, player.status().length() / 1000.0, false)
                fun waveEnergy(x: Int) = (0 until waveform.height).sumOf { y -> val rgb = waveform.getRGB(x, y); ((rgb shr 16 and 255) + (rgb shr 8 and 255) + (rgb and 255)).toLong() }
                assertTrue("Waveform must preserve leading empty edits", waveEnergy(320) < waveEnergy(1120))
            } finally { analysis.close(); println("Offset phase: analysis closed") }
        } finally { player.controls().stop(); component.release(); factory.release(); println("Offset phase: player released") }
    }

    @Test fun `http endpoint without an extension supports probing playback and MediaInfo`() {
        val bytes = Files.readAllBytes(fixture)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = Executors.newVirtualThreadPerTaskExecutor()
        server.executor = requests
        server.createContext("/stream") { exchange ->
            try {
                println("HTTP fixture: ${exchange.requestMethod} ${exchange.requestURI.path} Range=${exchange.requestHeaders.getFirst("Range")}")
                val range = exchange.requestHeaders.getFirst("Range")?.let { Regex("bytes=(\\d+)-(\\d*)").find(it) }
                val start = range?.groupValues?.get(1)?.toInt() ?: 0
                val end = range?.groupValues?.get(2)?.toIntOrNull()?.coerceAtMost(bytes.lastIndex) ?: bytes.lastIndex
                exchange.responseHeaders.set("Content-Type", "video/mp4")
                exchange.responseHeaders.set("Accept-Ranges", "bytes")
                if (range != null) exchange.responseHeaders.set("Content-Range", "bytes $start-$end/${bytes.size}")
                val length = end - start + 1
                exchange.responseHeaders.set("Content-Length", length.toString())
                exchange.sendResponseHeaders(if (range == null) 200 else 206, if (exchange.requestMethod == "HEAD") -1 else length.toLong())
                if (exchange.requestMethod != "HEAD") exchange.responseBody.write(bytes, start, length)
            } finally { exchange.close() }
        }
        server.start()
        try {
            val source = MediaSource.parse("http://127.0.0.1:${server.address.port}/stream?token=fixture-private-token")
            val info = MediaProbe.inspect(tool("ffprobe"), source)
            assertEquals(2, info.audio.size)
            LocalPreviewWindow(tool("ffmpeg"), source, info.video!!, info.analysisOrigin, 10.0).use { local ->
                local.focus(8.0)
                await { local.failure?.let { throw AssertionError(it) }; local.refined(7.8) != null && local.refined(8.2) != null }
            }
            val previews = PreviewStoryboard(tool("ffmpeg"), source, 0, 0.0, 10.0, temporary.newFolder("remote-previews").toPath())
            try {
                previews.request(3.0)
                await { previews.failure?.let { throw AssertionError(it) }; previews.peek(3.0) != null }
                previews.request(9.0)
                await { previews.failure?.let { throw AssertionError(it) }; previews.peek(9.0) != null }
                assertTrue(previews.readyCount <= 160)
            } finally { previews.close() }
            val json = ToolProcess.capture(tool("MediaInfo"), listOf("--Output=JSON", source.argument), source).toString(Charsets.UTF_8)
            assertTrue(JsonParser.parseString(json).asJsonObject.has("media"))
            NativeRuntime.loadVlc(root.resolve(if (windows) "vlc" else "vlc/lib"))
            val factory = MediaPlayerFactory(null, listOf("--aout=dummy", "--vout=dummy", "--quiet"))
            val player = factory.mediaPlayers().newMediaPlayer()
            try { assertTrue(player.media().startPaused(source.argument)); assertTrue(player.status().isSeekable) }
            finally { player.release(); factory.release() }
        } finally { server.stop(0); requests.shutdownNow() }
    }

    @Test fun `packaged probe and MediaInfo inspect unicode multi track video`() {
        val source = MediaSource.parse(fixture.toString())
        val info = MediaProbe.inspect(tool("ffprobe"), source)
        assertEquals(2, info.audio.size)
        assertEquals(320, info.video!!.width)
        assertTrue(info.duration!! >= 9.9)
        val metadata = ToolProcess.capture(tool("MediaInfo"), listOf("--Output=JSON", source.argument), source).toString(Charsets.UTF_8)
        val tracks = JsonParser.parseString(metadata).asJsonObject.getAsJsonObject("media").getAsJsonArray("track")
        assertEquals(2, tracks.count { it.asJsonObject.get("@type").asString == "Audio" })
    }

    @Test fun `callback video surface renders and playback controls work without system VLC`() {
        val vlc = root.resolve(if (windows) "vlc" else "vlc/lib")
        NativeRuntime.loadVlc(vlc)
        val factory = MediaPlayerFactory(null, listOf("--no-video-title-show", "--aout=dummy", "--quiet"))
        val rendered = AtomicInteger()
        val render = object : RenderCallback {
            override fun lock(player: MediaPlayer) = Unit
            override fun unlock(player: MediaPlayer) = Unit
            override fun display(player: MediaPlayer, buffers: Array<out ByteBuffer>, format: BufferFormat, width: Int, height: Int) { rendered.incrementAndGet() }
        }
        val format = object : BufferFormatCallback {
            override fun getBufferFormat(width: Int, height: Int): BufferFormat = RV32BufferFormat(width, height)
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
            override fun newFormatSize(width: Int, height: Int, aspectNumerator: Int, aspectDenominator: Int) = Unit
        }
        lateinit var component: CallbackMediaPlayerComponent
        SwingUtilities.invokeAndWait { component = CallbackMediaPlayerComponent(factory, null, InputEvents.NONE, true, render, format, JPanel()) }
        try {
            val player = component.mediaPlayer()
            assertTrue(player.media().startPaused(fixture.toString()))
            player.controls().play()
            await { rendered.get() > 2 && player.status().time() > 100 }
            assertTrue(player.status().isSeekable)
            val info = MediaProbe.inspect(tool("ffprobe"), MediaSource.parse(fixture.toString()))
            val ids = player.audio().trackDescriptions().map { it.id() }
            val selected = player.audio().track()
            assertNotNull("Selected native track must map to an actual source stream", info.audioStream(selected, ids))
            player.controls().setPause(true)
            player.controls().setTime(5000)
            await { kotlin.math.abs(player.status().time() - 5000) < 250 }
            assertTrue(player.controls().setRate(1.5f))
            player.controls().stop()
        } finally { component.release(); factory.release() }
    }

    @Test fun `live spectrum returns source timestamps and cancellation releases process`() {
        val source = MediaSource.parse(fixture.toString())
        val info = MediaProbe.inspect(tool("ffprobe"), source)
        val failure = AtomicReference<String>()
        val session = SpectrumSession(tool("ffmpeg"), source, info.audio.first(), info.analysisOrigin, 2.0, failed = failure::set)
        try {
            await { failure.get()?.let { throw AssertionError(it) }; session.frameAt(2.0) != null }
            val frame = session.frameAt(2.0)!!
            assertTrue(kotlin.math.abs(frame.time - 2.0) <= .2)
            assertEquals(640, frame.image.width)
            assertEquals(256, frame.image.height)
        } finally { session.close() }
        await { ProcessHandle.current().children().use { children -> children.noneMatch { it.info().command().orElse("").contains("ffmpeg", true) } } }
    }

    @Test fun `centered live window decodes upcoming audio and remains valid at EOF`() {
        val path = temporary.root.toPath().resolve("future-tone.wav")
        ToolProcess.capture(tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i",
            "aevalsrc=0.8*sin(2*PI*440*t)*between(t\\,4\\,5):s=48000:d=6", path.toString()))
        val source = MediaSource.parse(path.toString())
        val info = MediaProbe.inspect(tool("ffprobe"), source)
        for (position in listOf(2.0, 6.0)) {
            val error = AtomicReference<String>()
            val session = SpectrumSession(tool("ffmpeg"), source, info.audio.single(), info.analysisOrigin, position, failed = error::set)
            try {
                await { error.get()?.let { throw AssertionError(it) }; session.frameAt(position) != null }
                val frame = session.frameAt(position)!!
                assertEquals(position, frame.time, .2)
                assertEquals(session.windowSeconds / 2, session.lookAheadSeconds, .001)
                fun energy(x: Int) = (0 until frame.image.height).sumOf { y -> val rgb = frame.image.getRGB(x, y); ((rgb shr 16 and 255) + (rgb shr 8 and 255) + (rgb and 255)).toLong() }
                if (position == 2.0) assertTrue("Upcoming tone must appear to the right of the central cursor", energy(530) > energy(160) * 2)
                else assertTrue("Past tone stays left and the post-EOF area stays silent", energy(180) > energy(530) * 2)
            } finally { session.close() }
        }
    }

    @Test fun `full file spectrum produces an actual bounded image`() {
        val source = MediaSource.parse(fixture.toString())
        val info = MediaProbe.inspect(tool("ffprobe"), source)
        val image = SpectrumSession.overview(tool("ffmpeg"), source, info.audio.first(), info.analysisOrigin, info.duration!!)
        assertEquals(1600, image.width)
        assertEquals(512, image.height)
    }

    @Test fun `gif apng and animated webp retain variable delays and are seekable`() {
        val directory = temporary.newFolder("animations").toPath()
        val colors = listOf(Color(255, 0, 0, 255), Color(0, 255, 0, 128), Color(0, 0, 255, 255))
        colors.forEachIndexed { index, color ->
            val frame = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
            frame.createGraphics().apply { this.color = color; fillRect(0, 0, 32, 32); dispose() }
            ImageIO.write(frame, "png", directory.resolve("$index.png").toFile())
        }
        val list = directory.resolve("frames.txt")
        Files.writeString(list, "file '0.png'\nduration 0.08\nfile '1.png'\nduration 0.24\nfile '2.png'\nduration 0.16\nfile '2.png'\n")
        for (extension in listOf("gif", "apng", "webp")) {
            val path = directory.resolve("animation.$extension")
            val codec = when (extension) { "webp" -> listOf("-c:v", "libwebp_anim", "-loop", "0"); "apng" -> listOf("-plays", "0"); else -> listOf("-loop", "0") }
            ToolProcess.capture(tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "concat", "-safe", "0", "-i", list.toString(), "-fps_mode", "vfr") + codec + path.toString())
            val source = MediaSource.parse(path.toString())
            assertEquals("$extension repeats indefinitely according to its metadata", 0, AnimationLoops.read(source))
            val timeline = AnimationTimeline(MediaProbe.animationFrames(tool("ffprobe"), source))
            assertTrue("$extension needs multiple frames", timeline.frames.size >= 3)
            assertTrue("$extension must keep unequal durations", timeline.frames.map { kotlin.math.round(it.duration * 100) }.distinct().size > 1)
            val target = 1
            val failure = AtomicReference<String>()
            val decoder = AnimationDecoder(tool("ffmpeg"), source, timeline, 32, 32, target, failure::set)
            try {
                await { failure.get()?.let { throw AssertionError("$extension: $it") }; decoder.frameAt(timeline.frames[target].time) != null }
                val image = decoder.frameAt(timeline.frames[target].time)!!.image
                assertEquals(32, image.width)
                assertTrue("$extension selected frame is green", Color(image.getRGB(16, 16), true).green > 150)
            } finally { decoder.close() }
        }
    }

    companion object {
        @ClassRule @JvmField val temporary = TemporaryFolder()
        private val root: Path = Path.of(System.getProperty("anyplayer.nativeRoot"))
        private val windows = System.getProperty("os.name").startsWith("Windows")
        private lateinit var fixture: Path
        private fun tool(name: String) = root.resolve("bin").resolve(name.lowercase() + if (windows) ".exe" else "")

        @BeforeClass @JvmStatic fun createFixture() {
            fixture = temporary.root.toPath().resolve("sample & тест.mp4")
            ToolProcess.capture(tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "testsrc2=size=320x180:rate=24:duration=10", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000:duration=10", "-f", "lavfi", "-i", "sine=frequency=880:sample_rate=48000:duration=10", "-map", "0:v", "-map", "1:a", "-map", "2:a", "-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", "-metadata:s:a:0", "title=Original", "-metadata:s:a:1", "title=Commentary", fixture.toString()))
        }

        private fun await(condition: () -> Boolean) {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!condition()) {
                if (System.nanoTime() > until) throw AssertionError("Native operation did not reach the expected state within 15 seconds.")
                Thread.sleep(20)
            }
        }
    }
}
