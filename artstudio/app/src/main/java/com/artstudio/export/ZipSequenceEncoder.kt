package com.artstudio.export

import com.artstudio.animation.AnimationTimeline
import com.artstudio.document.model.ArtDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Universal frame sequence exporter packaging lossless PNG frames and a timing manifest into a ZIP container.
 * Serves as the universal desktop fallback format per ADR-044.
 */
object ZipSequenceEncoder {

    fun exportSequence(
        frameBuffers: List<IntArray>,
        width: Int,
        height: Int,
        timeline: AnimationTimeline,
        outputFile: File,
        cancellationToken: ExportCancellationToken? = null
    ) {
        val totalFrames = frameBuffers.size
        require(totalFrames > 0) { "Must provide at least 1 frame to export" }

        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos.buffered()).use { zos ->
                // 1. Write manifest.json
                val manifestJson = buildManifest(timeline, width, height, totalFrames)
                val manifestEntry = ZipEntry("manifest.json")
                zos.putNextEntry(manifestEntry)
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. Write PNG frames
                val padLength = maxOf(4, totalFrames.toString().length)
                for (i in frameBuffers.indices) {
                    if (cancellationToken?.isCancelled == true) {
                        throw ExportCancelledException()
                    }

                    val frameNum = String.format("%0${padLength}d", i + 1)
                    val frameName = "frames/frame_$frameNum.png"
                    val entry = ZipEntry(frameName)
                    zos.putNextEntry(entry)

                    val baos = ByteArrayOutputStream()
                    PureKotlinPngEncoder.encodeRgba(
                        pixels = frameBuffers[i],
                        width = width,
                        height = height,
                        outputStream = baos
                    )
                    zos.write(baos.toByteArray())
                    zos.closeEntry()
                }

                zos.finish()
            }
        }
    }

    private fun buildManifest(
        timeline: AnimationTimeline,
        width: Int,
        height: Int,
        frameCount: Int
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"format\": \"artstudio_frame_sequence\",\n")
        sb.append("  \"version\": \"1.0.0\",\n")
        sb.append("  \"width\": $width,\n")
        sb.append("  \"height\": $height,\n")
        sb.append("  \"fps\": ${timeline.fps},\n")
        sb.append("  \"frameCount\": $frameCount,\n")
        sb.append("  \"totalDurationTicks\": ${timeline.totalDurationTicks},\n")
        sb.append("  \"totalDurationMs\": ${timeline.totalDurationMs},\n")
        sb.append("  \"loopMode\": \"${timeline.loopMode.id}\",\n")
        sb.append("  \"frames\": [\n")

        for (i in 0 until frameCount) {
            val f = timeline.frames.getOrNull(i)
            val durationTicks = f?.durationTicks ?: 1
            val durationMs = ((durationTicks.toDouble() / timeline.fps) * 1000.0).toLong()
            val padLength = maxOf(4, frameCount.toString().length)
            val frameNum = String.format("%0${padLength}d", i + 1)
            val fileRel = "frames/frame_$frameNum.png"

            sb.append("    {\n")
            sb.append("      \"index\": $i,\n")
            sb.append("      \"fileName\": \"$fileRel\",\n")
            sb.append("      \"name\": \"${f?.name ?: "Frame ${i + 1}"}\",\n")
            sb.append("      \"durationTicks\": $durationTicks,\n")
            sb.append("      \"durationMs\": $durationMs\n")
            sb.append(if (i == frameCount - 1) "    }\n" else "    },\n")
        }

        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }
}
