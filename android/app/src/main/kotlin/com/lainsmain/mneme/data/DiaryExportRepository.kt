package com.lainsmain.mneme.data

import android.content.Context
import android.net.Uri
import com.lainsmain.mneme.model.InlineStyle
import com.lainsmain.mneme.model.RichTextDocument
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class DiaryExportResult(val entryCount: Int, val photoCount: Int, val recapCount: Int)

@Serializable
private data class PortableDiaryManifest(
    val format: String = "mneme-portable-export-v2",
    val createdAt: String,
    val entries: List<PortableEntry>,
    val photos: List<PortablePhoto>,
    val monthlyRecaps: List<PortableRecap>,
)

@Serializable
private data class PortableEntry(
    val id: String,
    val date: String,
    val documentJson: String,
    val plainText: String,
    val favorite: Boolean,
    val locationName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val revision: Long,
)

@Serializable
private data class PortablePhoto(
    val id: String,
    val entryId: String,
    val mediaPath: String,
    val metadataPath: String,
    val caption: String,
    val sortOrder: Int,
    val sha256: String,
)

@Serializable
private data class PortableRecap(
    val id: String,
    val yearMonth: String,
    val documentJson: String,
    val plainText: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val revision: Long,
)

class DiaryExportRepository(
    private val context: Context,
    private val diaryDao: DiaryDao,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) {
    suspend fun export(destination: Uri): Result<DiaryExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = diaryDao.exportSnapshot()
            val photosByPage = snapshot.attachments.groupBy(AttachmentEntity::pageId)
            val output = requireNotNull(context.contentResolver.openOutputStream(destination)) {
                "Android could not open the chosen export file."
            }
            ZipOutputStream(output.buffered()).use { zip ->
                val createdAt = Instant.now().toString()
                zip.writeText(
                    "README.txt",
                    "Mneme diary export\nCreated $createdAt\n\n" +
                        "Open index.html in a browser, or read the Markdown files in entries/ and recaps/. " +
                        "Original photos and their metadata are in media/. manifest.json contains a complete " +
                        "machine-readable index for future tools and migrations. This archive is not encrypted.\n",
                )
                val indexRows = snapshot.pages.sortedByDescending(DiaryPageEntity::diaryDate).joinToString("\n") { page ->
                    "<li class=\"${if (page.isFavorite) "favorite" else ""}\">" +
                        "${if (page.isFavorite) "<span title=\"Favorite\">★</span> " else ""}" +
                        "<a href=\"entries/${page.diaryDate}.html\">${page.diaryDate}</a>" +
                        " — ${escapeHtml(page.plainText.take(120))}</li>"
                }
                val recapRows = snapshot.recaps.sortedByDescending(MonthlyRecapEntity::yearMonth).joinToString("\n") {
                    "<li><a href=\"recaps/${it.yearMonth}.html\">${it.yearMonth}</a></li>"
                }
                zip.writeText(
                    "index.html",
                    htmlPage("Mneme diary", "<h1>Mneme diary</h1><h2>Entries</h2><ul>$indexRows</ul>" +
                        "<h2>Monthly recaps</h2><ul>$recapRows</ul>"),
                )
                zip.writeText("manifest.json", portableManifest(snapshot, createdAt))
                snapshot.pages.forEach { page ->
                    val document = decode(page.documentJson, page.plainText)
                    val photoLinks = photosByPage[page.id].orEmpty().joinToString("\n") { photo ->
                        val fileName = photoExportName(photo)
                        "<figure><a href=\"../media/$fileName\"><img src=\"../media/$fileName\"></a>" +
                            photo.caption.takeIf(String::isNotBlank)
                                ?.let { "<figcaption>${escapeHtml(it)}</figcaption>" }
                                .orEmpty() + "</figure>"
                    }
                    val coordinates = if (page.latitude != null && page.longitude != null) {
                        " <span>(${page.latitude}, ${page.longitude})</span>"
                    } else ""
                    val location = page.locationName
                        ?.let { "<p class=\"meta\">📍 ${escapeHtml(it)}$coordinates</p>" }
                        .orEmpty()
                    val favorite = if (page.isFavorite) "<p class=\"favorite\">★ Favorite</p>" else ""
                    zip.writeText(
                        "entries/${page.diaryDate}.html",
                        htmlPage(page.diaryDate, "<h1>${page.diaryDate}</h1>$favorite$location<div class=\"writing\">" +
                            renderHtml(document) + "</div><div class=\"photos\">$photoLinks</div>"),
                    )
                    val photoMarkdown = photosByPage[page.id].orEmpty().joinToString("\n\n") { photo ->
                        val fileName = photoExportName(photo)
                        val caption = photo.caption.ifBlank { photo.originalFileName ?: "Photo" }
                        "![${escapeMarkdown(caption)}](../media/$fileName)" +
                            photo.caption.takeIf(String::isNotBlank)?.let { "\n\n_${escapeMarkdown(it)}_" }.orEmpty()
                    }
                    val favoriteMarkdown = if (page.isFavorite) "★ Favorite\n\n" else ""
                    val locationMarkdown = page.locationName?.let {
                        val coordinateText = if (page.latitude != null && page.longitude != null) {
                            " (${page.latitude}, ${page.longitude})"
                        } else ""
                        "Location: $it$coordinateText\n\n"
                    }.orEmpty()
                    val photosMarkdown = if (photoMarkdown.isNotBlank()) {
                        "\n\n## Photos\n\n$photoMarkdown\n"
                    } else "\n"
                    zip.writeText(
                        "entries/${page.diaryDate}.md",
                        "# ${page.diaryDate}\n\n$favoriteMarkdown$locationMarkdown" +
                            renderMarkdown(document) + photosMarkdown,
                    )
                }
                snapshot.recaps.forEach { recap ->
                    val document = decode(recap.documentJson, recap.plainText)
                    zip.writeText("recaps/${recap.yearMonth}.html", htmlPage("Recap ${recap.yearMonth}", "<h1>Recap ${recap.yearMonth}</h1>${renderHtml(document)}"))
                    zip.writeText("recaps/${recap.yearMonth}.md", "# Recap ${recap.yearMonth}\n\n${renderMarkdown(document)}\n")
                }
                snapshot.attachments.forEach { photo ->
                    val fileName = photoExportName(photo)
                    val source = File(photo.encryptedFileName)
                    require(source.isFile) { "A photo is missing from local storage: ${photo.originalFileName ?: photo.id}" }
                    zip.putNextEntry(ZipEntry("media/$fileName"))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    zip.writeText("media/$fileName.metadata.json", photoMetadata(photo))
                }
            }
            DiaryExportResult(snapshot.pages.size, snapshot.attachments.size, snapshot.recaps.size)
        }
    }

    private fun decode(encoded: String, fallback: String): RichTextDocument = runCatching {
        json.decodeFromString(RichTextDocument.serializer(), encoded)
    }.getOrElse { RichTextDocument(fallback) }

    private fun renderHtml(document: RichTextDocument): String = renderRuns(document) { text, styles ->
        var rendered = escapeHtml(text).replace("\n", "<br>\n")
        if (InlineStyle.Bold in styles) rendered = "<strong>$rendered</strong>"
        if (InlineStyle.Italic in styles) rendered = "<em>$rendered</em>"
        if (InlineStyle.Underline in styles) rendered = "<u>$rendered</u>"
        if (InlineStyle.StrikeThrough in styles) rendered = "<s>$rendered</s>"
        if (InlineStyle.Heading in styles) rendered = "<span class=\"heading\">$rendered</span>"
        rendered
    }

    private fun renderMarkdown(document: RichTextDocument): String = renderRuns(document) { text, styles ->
        var rendered = text
        if (InlineStyle.Bold in styles) rendered = "**$rendered**"
        if (InlineStyle.Italic in styles) rendered = "*$rendered*"
        if (InlineStyle.Underline in styles) rendered = "<u>$rendered</u>"
        if (InlineStyle.StrikeThrough in styles) rendered = "~~$rendered~~"
        if (InlineStyle.Heading in styles) rendered = "**$rendered**"
        rendered
    }

    private fun renderRuns(
        document: RichTextDocument,
        render: (String, Set<InlineStyle>) -> String,
    ): String {
        if (document.text.isEmpty()) return ""
        val result = StringBuilder()
        var start = 0
        while (start < document.text.length) {
            val styles = document.stylesAt(start)
            var end = start + 1
            while (end < document.text.length && document.stylesAt(end) == styles) end++
            result.append(render(document.text.substring(start, end), styles))
            start = end
        }
        return result.toString()
    }

    private fun photoExportName(photo: AttachmentEntity): String {
        val original = photo.originalFileName?.substringAfterLast('/')?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "${photo.id.take(8)}-${original?.takeIf(String::isNotBlank) ?: "photo"}"
    }

    private fun photoMetadata(photo: AttachmentEntity): String = buildJsonObject {
        put("id", photo.id)
        put("originalFileName", photo.originalFileName)
        put("mimeType", photo.mimeType)
        put("byteSize", photo.byteSize)
        put("width", photo.width)
        put("height", photo.height)
        put("capturedAtEpochMillis", photo.capturedAtEpochMillis)
        put("latitude", photo.latitude)
        put("longitude", photo.longitude)
        put("altitudeMeters", photo.altitudeMeters)
        put("cameraMake", photo.cameraMake)
        put("cameraModel", photo.cameraModel)
        put("lensModel", photo.lensModel)
        put("exposureTime", photo.exposureTime)
        put("aperture", photo.aperture)
        put("iso", photo.iso)
        put("focalLength", photo.focalLength)
        put("caption", photo.caption)
        put("sha256", photo.sha256)
        put("normalizedExif", photo.normalizedExifJson)
    }.let(json::encodeToString)

    private fun portableManifest(snapshot: DiaryExportSnapshot, createdAt: String): String =
        json.encodeToString(
            PortableDiaryManifest(
                createdAt = createdAt,
                entries = snapshot.pages.map { page ->
                    PortableEntry(
                        id = page.id,
                        date = page.diaryDate,
                        documentJson = page.documentJson,
                        plainText = page.plainText,
                        favorite = page.isFavorite,
                        locationName = page.locationName,
                        latitude = page.latitude,
                        longitude = page.longitude,
                        createdAtEpochMillis = page.createdAtEpochMillis,
                        updatedAtEpochMillis = page.updatedAtEpochMillis,
                        revision = page.revision,
                    )
                },
                photos = snapshot.attachments.map { photo ->
                    val fileName = photoExportName(photo)
                    PortablePhoto(
                        id = photo.id,
                        entryId = photo.pageId,
                        mediaPath = "media/$fileName",
                        metadataPath = "media/$fileName.metadata.json",
                        caption = photo.caption,
                        sortOrder = photo.sortOrder,
                        sha256 = photo.sha256,
                    )
                },
                monthlyRecaps = snapshot.recaps.map { recap ->
                    PortableRecap(
                        id = recap.id,
                        yearMonth = recap.yearMonth,
                        documentJson = recap.documentJson,
                        plainText = recap.plainText,
                        createdAtEpochMillis = recap.createdAtEpochMillis,
                        updatedAtEpochMillis = recap.updatedAtEpochMillis,
                        revision = recap.revision,
                    )
                },
            ),
        )

    private fun htmlPage(title: String, body: String) = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>${escapeHtml(title)}</title><style>
body{max-width:760px;margin:40px auto;padding:0 20px;font:17px/1.65 system-ui;color:#242128;background:#faf8fc}
a{color:#6750a4}.meta{color:#68636d}.writing{white-space:normal}.heading{font-size:1.35em;font-weight:700}
.favorite{color:#9a6b12}
.photos{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:24px}figure{margin:0}img{width:100%;height:auto;border-radius:14px}figcaption{font-size:.85em;color:#68636d}
</style></head><body>$body</body></html>"""

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    private fun escapeMarkdown(value: String): String = value.replace("[", "\\[").replace("]", "\\]")

    private fun ZipOutputStream.writeText(path: String, value: String) {
        putNextEntry(ZipEntry(path))
        write(value.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
