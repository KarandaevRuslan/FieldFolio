package com.karandaev.fieldfolio.core.document.runtime

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.weather.*
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentRuntimeTest {
  private val context
    get() = RuntimeEnvironment.getApplication()

  private val scope = "11111111-2222-3333-4444-555555555555"
  private val host = documentHost("fixture", "document", "fixture")
  private var weatherRequests = 0
  private val weather =
    object : WeatherProvider {
      override suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherInfo> {
        weatherRequests++
        error("Document API must never make a network request")
      }

      override suspend fun cachedWeather(latitude: Double, longitude: Double) =
        WeatherInfo(18.5, 2, "Europe/London")
    }

  private fun image() =
    ByteArrayOutputStream()
      .also { out ->
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.CYAN)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
      }
      .toByteArray()

  private fun access(): DocumentAccessContext {
    val ring = listOf(NormalizedPoint(.1, .1), NormalizedPoint(.9, .1), NormalizedPoint(.9, .9))
    val result =
      ModelResultData(
        modelId = "example",
        modelName = "Model",
        provider = "local",
        task = "cv_segmentation",
        inputAsset = "resources/image.png",
        maskAsset = "resources/mask.png",
        boxes = listOf(ResultBox("object", .9, .1, .2, .7, .8)),
        scores = listOf(ResultScore("class", .8)),
        values = listOf(ResultValue("value", 42.0, "m")),
        text = "model output",
        inputText = "model input",
        inputKind = "image",
        layers =
          listOf(
            SegmentationLayer(
              "pixel and polygon",
              .85,
              "resources/mask.png",
              listOf(SegmentationPolygon(listOf(ring))),
            )
          ),
      )
    val children =
      listOf(
          DocumentNode(
            id = "document",
            name = "Dashboard",
            kind = BlockKind.DOCUMENT,
            body = DefaultNotebookDocument,
            syntaxExtension = "html",
          ),
          DocumentNode(
            id = "text",
            name = "Notes",
            displayName = "Visual alias",
            kind = BlockKind.TEXT,
            body = "PRIVATE_DEMO_PAYLOAD",
            visible = false,
          ),
          DocumentNode(
            id = "heading",
            name = "Heading",
            kind = BlockKind.HEADING,
            body = "Subtitle",
          ),
          DocumentNode(
            id = "file",
            name = "Config.json",
            kind = BlockKind.TEXT_FILE,
            asset = "resources/data.json",
            mime = "application/json",
          ),
          DocumentNode(
            id = "image",
            name = "Фото one.png",
            kind = BlockKind.IMAGE,
            asset = "resources/image.png",
            mime = "image/png",
          ),
          DocumentNode(
            id = "audio",
            name = "Song",
            kind = BlockKind.AUDIO,
            asset = "resources/audio.wav",
            mime = "audio/wav",
            audio =
              AudioPresentation(
                overrides = mapOf("title" to "Song title"),
                coverAsset = "resources/image.png",
              ),
          ),
          DocumentNode(
            id = "location",
            name = "Location",
            kind = BlockKind.LOCATION,
            deviceLocation = true,
          ),
          DocumentNode(
            id = "weather",
            name = "Weather",
            kind = BlockKind.WEATHER,
            latitude = 51.5,
            longitude = -.1,
          ),
          DocumentNode(
            id = "clock",
            name = "Clock",
            kind = BlockKind.CLOCK,
            zoneId = "Europe/London",
          ),
          DocumentNode(
            id = "model",
            name = "Mask result",
            kind = BlockKind.MODEL_RESULT,
            result = result,
          ),
          DocumentNode(
            id = "classify",
            name = "Classification",
            kind = BlockKind.MODEL_RESULT,
            result =
              result.copy(
                id = "classification",
                task = "cv_classification",
                boxes = emptyList(),
                layers = emptyList(),
                maskAsset = null,
              ),
          ),
          DocumentNode(
            id = "regress",
            name = "Regression",
            kind = BlockKind.MODEL_RESULT,
            result =
              result.copy(
                id = "regression",
                task = "cv_regression",
                boxes = emptyList(),
                layers = emptyList(),
                maskAsset = null,
              ),
          ),
          DocumentNode(
            id = "nlp",
            name = "Text model",
            kind = BlockKind.MODEL_RESULT,
            result =
              result.copy(
                id = "text-result",
                task = "text_generation",
                inputKind = "text",
                inputAsset = null,
                maskAsset = null,
                boxes = emptyList(),
                layers = emptyList(),
              ),
          ),
          DocumentNode(
            id = "numeric",
            name = "Numeric model",
            kind = BlockKind.MODEL_RESULT,
            result =
              result.copy(
                id = "numeric-result",
                task = "numeric_regression",
                inputKind = "numbers",
                inputText = "[1,2,3]",
                inputAsset = null,
                maskAsset = null,
                boxes = emptyList(),
                layers = emptyList(),
              ),
          ),
          DocumentNode(
            id = "special",
            name = "A/B:100%.txt",
            kind = BlockKind.TEXT,
            body = "special logical name",
          ),
        )
        .mapIndexed { i, n -> n.copy(parentId = "folder", order = i) }
    val doc =
      ReportDocument(
        nodes =
          listOf(DocumentNode(id = "folder", name = "Data", kind = BlockKind.FOLDER)) + children
      )
    val utf16 =
      byteArrayOf(0xff.toByte(), 0xfe.toByte()) +
        "{\"count\":4,\"word\":\"текст\"}".toByteArray(Charsets.UTF_16LE)
    return DocumentAccessContext(
      "fixture",
      doc,
      mapOf(
        "resources/image.png" to image(),
        "resources/mask.png" to image(),
        "resources/data.json" to utf16,
        "resources/audio.wav" to ByteArray(64),
      ),
      "Report title",
      "Report description",
      device = { MapPoint(10.0, 20.0) },
      weather = weather,
    )
  }

  private fun resource(): DocumentResources = runBlocking { access().resources() }

  private fun manifest(resources: DocumentResources) = resources.manifest(host, scope, "document")

  private fun entry(manifest: JSONObject, id: String): JSONObject =
    manifest.getJSONArray("entries").let { entries ->
      (0 until entries.length())
        .map { entries.getJSONObject(it) }
        .single { it.getString("id") == id }
    }

  private fun metadata(resources: DocumentResources, id: String): JSONObject {
    val uri = Uri.parse(entry(manifest(resources), id).getString("metadataUrl"))
    return JSONObject(
      resources.response(uri, host, scope)!!.data.bufferedReader().use { it.readText() }
    )
  }

  @Test
  fun everyBlockKindIsExposedByLogicalNameWithoutInventingDocumentResources() {
    val resources = resource()
    val manifest = manifest(resources)
    assertEquals(BlockKind.entries.toSet(), resources.tree.document.nodes.map { it.kind }.toSet())
    assertEquals("/Data", manifest.getString("base"))
    val own = entry(manifest, "document")
    assertEquals("/Data/Dashboard", own.getString("path"))
    assertEquals("document", own.getString("kind"))
    assertEquals(1, own.getJSONObject("assets").length())
    assertEquals("PRIVATE_DEMO_PAYLOAD", metadata(resources, "text").getString("text"))
    assertFalse(entry(manifest, "text").getBoolean("visible"))
    assertEquals("/Data/A%2FB%3A100%25.txt", entry(manifest, "special").getString("path"))
    assertEquals(
      "/Data/%D0%A4%D0%BE%D1%82%D0%BE%20one.png",
      entry(manifest, "image").getString("path"),
    )
    assertFalse(own.has("privateResources"))
  }

  @Test
  fun fullModelResultsKeepPixelsPolygonsScoresRegressionTextAndNumericInputs() {
    val resources = resource()
    val result = metadata(resources, "model").getJSONObject("result")
    assertEquals(.9, result.getJSONArray("boxes").getJSONObject(0).getDouble("confidence"), .001)
    assertEquals(42.0, result.getJSONArray("values").getJSONObject(0).getDouble("value"), .001)
    assertEquals(.8, result.getJSONArray("scores").getJSONObject(0).getDouble("score"), .001)
    val layer = result.getJSONArray("layers").getJSONObject(0)
    assertEquals(
      3,
      layer.getJSONArray("polygons").getJSONObject(0).getJSONArray("rings").getJSONArray(0).length(),
    )
    assertTrue(layer.getString("maskUrl").startsWith("https://$host/vfs/$scope/"))
    assertFalse(result.has("inputAsset"))
    assertFalse(layer.has("maskAsset"))
    assertEquals(
      "image/png",
      entry(manifest(resources), "model")
        .getJSONObject("assets")
        .getJSONObject("input")
        .getString("mime"),
    )
    assertEquals("text", metadata(resources, "nlp").getJSONObject("result").getString("inputKind"))
    assertEquals(
      "numbers",
      metadata(resources, "numeric").getJSONObject("result").getString("inputKind"),
    )
    assertEquals(
      "cv_regression",
      metadata(resources, "regress").getJSONObject("result").getString("task"),
    )
  }

  @Test
  fun clockLocationCachedWeatherTextEncodingsAndAudioArtworkAreTypedAndOffline() {
    val resources = resource()
    assertEquals(
      10.0,
      metadata(resources, "location").getJSONObject("resolvedLocation").getDouble("latitude"),
      .001,
    )
    assertEquals("Europe/London", metadata(resources, "clock").getString("resolvedZoneId"))
    assertEquals(
      18.5,
      metadata(resources, "weather").getJSONObject("weather").getDouble("temperature"),
      .001,
    )
    assertEquals(0, weatherRequests)
    assertTrue(metadata(resources, "file").getString("text").contains("текст"))
    val audio = metadata(resources, "audio").getJSONObject("audio")
    assertEquals("Song title", audio.getJSONObject("displayedTags").getString("title"))
    assertTrue(audio.getString("coverUrl").contains("/vfs/$scope/"))
  }

  @Test
  fun resourceCapabilitiesRejectOtherReportsTraversalQueriesAndNativePaths() {
    val resources = resource()
    val good =
      entry(manifest(resources), "image")
        .getJSONObject("assets")
        .getJSONObject("data")
        .getString("url")
    assertEquals(200, resources.response(Uri.parse(good), host, scope)!!.statusCode)
    for (bad in
      listOf(
        good.replace(scope, "another-secret-scope"),
        good.replace(host, "other.invalid"),
        "$good?path=/private",
        good.replace("/data", "/%2e%2e"),
        "file:///private/data",
        "content://media/file",
        "https://$host/assets/acode/editor.html",
      )) assertNull(bad, resources.response(Uri.parse(bad), host, scope))
  }

  @Test
  fun nativeClientServesOnlyTheTrustedHostSandboxAndCurrentReportAndRevokesOnLock() {
    val resources = resource()
    val client = DocumentWebClient(context, host, scope, resources, {}, {})
    assertNotNull(client.intercept(Uri.parse(client.page)).data)
    val sandbox = client.intercept(Uri.parse(client.renderer))
    assertNotNull(sandbox.data)
    assertEquals("null", sandbox.responseHeaders["Access-Control-Allow-Origin"])
    assertTrue(
      sandbox.responseHeaders.getValue("Content-Security-Policy").contains("sandbox allow-scripts")
    )
    assertFalse(
      sandbox.responseHeaders.getValue("Content-Security-Policy").contains("allow-same-origin")
    )
    val asset = entry(manifest(resources), "image").getString("metadataUrl")
    for (bad in
      listOf(
        "https://example.invalid/",
        "https://$host/assets/acode/editor.html",
        "file:///data/data",
        "content://media/1",
      )) assertEquals(403, client.intercept(Uri.parse(bad)).statusCode)
    assertEquals(403, client.intercept(Uri.parse(asset), "POST").statusCode)
    assertEquals(200, client.intercept(Uri.parse(asset)).statusCode)
    client.close()
    assertEquals(403, client.intercept(Uri.parse(asset)).statusCode)
  }

  @Test
  fun rendererHasNoNativeBridgeFileAccessPersistentStorageOrPermissionPrompts() {
    val client = DocumentWebClient(context, host, scope, null, {}, {})
    val view = createDocumentWebView(context, client)
    try {
      assertNull(Shadows.shadowOf(view).getJavascriptInterface("NativeEditor"))
      assertFalse(view.settings.allowFileAccess)
      assertFalse(view.settings.allowContentAccess)
      assertFalse(view.settings.allowUniversalAccessFromFileURLs)
      assertFalse(view.settings.domStorageEnabled)
      assertFalse(view.settings.databaseEnabled)
      assertFalse(view.isSaveEnabled)
      assertFalse(CookieManager.getInstance().acceptCookie())
      var denied = false
      val permission =
        object : PermissionRequest() {
          override fun getOrigin() = Uri.parse("https://$host")

          override fun getResources() = arrayOf(RESOURCE_AUDIO_CAPTURE, RESOURCE_VIDEO_CAPTURE)

          override fun grant(resources: Array<out String>?) {
            fail("Unexpected camera/microphone grant")
          }

          override fun deny() {
            denied = true
          }
        }
      view.webChromeClient!!.onPermissionRequest(permission)
      assertTrue(denied)
      var completed = false
      assertTrue(
        view.webChromeClient!!.onShowFileChooser(
          view,
          { value ->
            assertNull(value)
            completed = true
          },
          null,
        )
      )
      assertTrue(completed)
    } finally {
      view.destroy()
    }
  }

  @Test
  fun documentSourceAndReferencesRoundTripThroughTheExistingSchemaAndCannotOwnAnAsset() {
    val original = access().document
    val adapter = MoshiFactory.create().adapter(ReportDocument::class.java)
    val decoded = adapter.fromJson(adapter.toJson(original))!!
    assertEquals(original, decoded)
    assertNull(decoded.nodes.single { it.kind == BlockKind.DOCUMENT }.asset)
    assertEquals(
      setOf(
        "resources/data.json",
        "resources/image.png",
        "resources/audio.wav",
        "resources/mask.png",
      ),
      decoded.resourcePaths(),
    )
    assertThrows(IllegalArgumentException::class.java) {
      DocumentTree(
        ReportDocument(
          nodes =
            listOf(
              DocumentNode(
                name = "Bad",
                kind = BlockKind.DOCUMENT,
                asset = "resources/private/own.html",
              )
            )
        )
      )
    }
  }

  @Test
  fun browserFixtureIsGeneratedFromActualNativeApiResponsesForEverySupportedKind() {
    val resources = resource()
    val manifest = manifest(resources)
    val client = DocumentWebClient(context, host, scope, resources, {}, {})
    val urls = linkedSetOf<String>()
    val entries = manifest.getJSONArray("entries")
    for (i in 0 until entries.length()) {
      val e = entries.getJSONObject(i)
      urls += e.getString("metadataUrl")
      val assets = e.getJSONObject("assets")
      assets.keys().forEach { role -> urls += assets.getJSONObject(role).getString("url") }
    }
    val responses = JSONObject()
    urls.forEach { url ->
      val response = client.intercept(Uri.parse(url))
      assertEquals(url, 200, response.statusCode)
      responses.put(
        url,
        JSONObject()
          .put("mime", response.mimeType)
          .put("headers", JSONObject(response.responseHeaders))
          .put("base64", Base64.getEncoder().encodeToString(response.data.readBytes())),
      )
    }
    val out = java.io.File("build/document-fixture.json")
    out.parentFile!!.mkdirs()
    out.writeText(
      JSONObject()
        .put("manifest", manifest)
        .put("responses", responses)
        .put("source", DefaultNotebookDocument)
        .put("rendererCsp", DocumentWebClient.rendererCsp(host))
        .toString()
    )
    assertTrue(out.length() > 1000)
  }

  @Test
  fun reportAndAudioArtworkAreFieldsWithoutIndependentLogicalNames() = runBlocking {
    val old = access()
    val context =
      DocumentAccessContext(
        "cover-fields",
        old.document.copy(cover = "resources/image.png"),
        old.files,
      )
    val resources = context.resources()
    val report = manifest(resources).getJSONObject("report")
    assertEquals("image/png", report.getJSONObject("cover").getString("mime"))
    assertEquals(report.getString("coverUrl"), report.getJSONObject("cover").getString("url"))
    val audio = metadata(resources, "audio").getJSONObject("audio")
    assertEquals("image/png", audio.getJSONObject("cover").getString("mime"))
    assertFalse(audio.getJSONObject("cover").has("name"))
    assertFalse(report.getJSONObject("cover").has("name"))
  }
}
