package com.clineagentmobile.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenRouterCatalogTest {

    @Test
    fun `catalog only returns free models that support tools`() {
        val json = """
            {
              "data": [
                {
                  "id": "provider/coder:free",
                  "name": "Coder Free",
                  "pricing": { "prompt": "0", "completion": "0" },
                  "supported_parameters": ["tools", "tool_choice"]
                },
                {
                  "id": "provider/paid:free",
                  "name": "Not Free",
                  "pricing": { "prompt": "0", "completion": "0.000001" },
                  "supported_parameters": ["tools"]
                },
                {
                  "id": "provider/no-tools:free",
                  "name": "No Tools",
                  "pricing": { "prompt": "0", "completion": "0" },
                  "supported_parameters": ["temperature"]
                },
                {
                  "id": "provider/paid",
                  "name": "Paid",
                  "pricing": { "prompt": "0", "completion": "0" },
                  "supported_parameters": ["tools"]
                }
              ]
            }
        """.trimIndent()

        assertEquals(
            listOf(OpenRouterModel("provider/coder:free", "Coder Free")),
            OpenRouterCatalog.parseFreeToolModels(json)
        )
    }
}
