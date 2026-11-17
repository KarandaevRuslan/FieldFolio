package com.karandaev.fieldfolio.core.models

import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider
import org.junit.Assert.*
import org.junit.Test

class CatalogWorkflowTest {
  private class Remote(
    override val id: String,
    override val name: String,
    override val task: String,
  ) : ServerModelAdapter {
    override suspend fun analyze(provider: InspectionProvider, image: ByteArray?, text: String) =
      ModelResultData(modelId = id, modelName = name, provider = "SERVER", task = task)
  }

  @Test
  fun registrySupportsAnotherTaskWithoutChangingCatalogUiOrImportingServerFiles() {
    val registry = ServerModelRegistry(listOf(Remote("road", "Road", "DETECTION")))
    registry.register(Remote("roughness", "Roughness", "REGRESSION"))
    val all =
      catalogChoices(
        listOf(
          LocalModel("a", "Alpha", ModelAdapter.CLASSIFICATION, importedAt = 10),
          LocalModel("b", "Beta", ModelAdapter.REGRESSION, importedAt = 20),
        ),
        registry.models.value,
      )
    assertEquals(
      listOf("roughness"),
      queryModels(all, true, "rough", "REGRESSION", ModelSort.NAME_ASC).map { it.id },
    )
    assertEquals(
      listOf("b", "a"),
      queryModels(all, false, "", null, ModelSort.NEWEST).map { it.id },
    )
    assertEquals(
      listOf("a"),
      queryModels(all, false, "ALP", null, ModelSort.NAME_ASC).map { it.id },
    )
    assertTrue(queryModels(all, true, "", null, ModelSort.NAME_ASC).all { it.local == null })
  }

  @Test
  fun regressionIsADistinctTaskAndDoesNotImplyTextOrProbabilities() {
    val model =
      LocalModel(
        "r",
        "Distance",
        ModelAdapter.REGRESSION,
        settings = ModelSettings(regressionUnits = listOf("m")),
      )
    assertEquals("REGRESSION", model.task)
    assertFalse(model.adapter.isText)
    val result =
      ModelResultData(
        modelId = "r",
        modelName = model.name,
        provider = "LOCAL",
        task = model.task,
        values = listOf(ResultValue("Offset", -2.5, "m")),
      )
    val document =
      ReportDocument(
        nodes =
          listOf(DocumentNode(name = "Regression", kind = BlockKind.MODEL_RESULT, result = result))
      )
    DocumentTree(document)
    assertEquals(-2.5, result.values.single().value, 0.0)
    assertTrue(result.scores.isEmpty())
  }
}
