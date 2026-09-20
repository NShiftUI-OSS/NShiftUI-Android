package com.nshiftui

import com.nshiftui.rainbowparser.RainbowParser
import com.nshiftui.rainbowparser.ast.EmbeddedLanguage
import com.nshiftui.rainbowparser.ast.RainbowDocument
import com.nshiftui.rainbowparser.ast.RainbowNode
import com.nshiftui.rainbowparser.ast.RainbowObjectEntry
import com.nshiftui.rainbowparser.ast.RainbowParameter
import com.nshiftui.rainbowparser.ast.RainbowTaggedBody
import com.nshiftui.rainbowparser.ast.RainbowUseDeclaration
import com.nshiftui.rainbowparser.ast.RainbowValue
import com.nshiftui.rainbowparser.diagnostics.RainbowSourceLocation
import com.nshiftui.rainbowparser.diagnostics.RainbowSourceRange
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private data class SampleTextMetadata(val text: String, val size: Double) : NShiftMetadata

private fun defaultCompiler(
    catalog: NShiftRegisteredVersionCatalog = NShiftRegisteredVersionCatalog.forTesting(),
    defaultVersion: NShiftVersion? = null,
    slotMarkers: Set<String> = setOf("NavLeading", "NavCenter", "NavTrailing"),
    decoder: NShiftRainbowMetadataDecoding? = null,
): NShiftRainbowCompiler {
    var configuration = NShiftRainbowCompileConfiguration(
        slotMarkers = slotMarkers,
        defaultVersion = defaultVersion,
        catalog = catalog,
    )
    if (decoder != null) {
        configuration = configuration.registeringMetadataDecoder(decoder)
    }
    return NShiftRainbowCompiler(configuration)
}

private val textDecoder = NShiftRainbowMapMetadataDecoder("Text") { map ->
    SampleTextMetadata(
        text = map["text"] as String,
        size = (map["size"] as Number).toDouble(),
    )
}

class NShiftRainbowJSONTests : FunSpec({
    test("maps scalar array and object values") {
        val parameters = listOf(
            RainbowParameter("title", RainbowValue.StringValue("Hello")),
            RainbowParameter("count", RainbowValue.IntValue(3)),
            RainbowParameter("ratio", RainbowValue.DoubleValue(1.5)),
            RainbowParameter("enabled", RainbowValue.BoolValue(true)),
            RainbowParameter("empty", RainbowValue.Null),
            RainbowParameter("token", RainbowValue.Identifier("back")),
            RainbowParameter(
                "tags",
                RainbowValue.ArrayValue(listOf(RainbowValue.StringValue("a"), RainbowValue.StringValue("b"))),
            ),
            RainbowParameter(
                "nested",
                RainbowValue.ObjectValue(listOf(RainbowObjectEntry("inner", RainbowValue.IntValue(1)))),
            ),
            RainbowParameter("id", RainbowValue.StringValue("skip-me")),
            RainbowParameter("style", RainbowValue.ObjectValue(emptyList())),
        )
        val obj = NShiftRainbowJSON.dictionary(parameters)
        obj["title"] shouldBe "Hello"
        obj["count"] shouldBe 3
        obj["ratio"] shouldBe 1.5
        obj["enabled"] shouldBe true
        obj.containsKey("empty") shouldBe true
        obj["empty"] shouldBe null
        obj["token"] shouldBe "back"
        obj["tags"] shouldBe listOf("a", "b")
        (obj["nested"] as Map<*, *>)["inner"] shouldBe 1
        obj.containsKey("id") shouldBe false
        obj.containsKey("style") shouldBe false
    }

    test("throws unsupported tagged value") {
        val range = RainbowSourceRange(
            start = RainbowSourceLocation(0, 1, 1),
            end = RainbowSourceLocation(1, 1, 2),
        )
        shouldThrow<NShiftRainbowCompileError.UnsupportedTaggedValue> {
            NShiftRainbowJSON.jsonValue(
                RainbowValue.Tagged(EmbeddedLanguage.JSON, RainbowTaggedBody.Text("{}"), range),
            )
        }.language shouldBe "JSON"
    }

    test("metadata decoder returns null for empty parameters") {
        textDecoder.decode(emptyList()) shouldBe null
    }
})

class NShiftRainbowCompilerTests : FunSpec({
    test("compiles slots children and metadata") {
        val source = """
            use Screen@1.0.0
            use Text@1.0.0
            use Icon@1.0.0
            use Scroll@1.0.0

            Screen(backgroundColor: "#FFFFFF") {
              NavLeading {
                Icon(name: .back, size: 22)
              }
              NavTrailing {
                Icon(name: .settings, size: 22)
              }
              Scroll {
                Text(text: "Hello", size: 16)
              }
            }
        """.trimIndent()
        val model = defaultCompiler(
            defaultVersion = NShiftVersion(1, 0, 0),
            decoder = textDecoder,
        ).compile(source)
        model.name.rawValue shouldBe "Screen"
        model.version.rawValue shouldBe "1.0.0"
        model.children.size shouldBe 1
        model.children[0].name.rawValue shouldBe "Scroll"
        model.slots[NShiftSlotName("NavLeading")]?.size shouldBe 1
        model.slots[NShiftSlotName("NavLeading")]?.get(0)?.name?.rawValue shouldBe "Icon"
        model.slots[NShiftSlotName("NavTrailing")]?.size shouldBe 1
        val text = model.children[0].children[0]
        text.name.rawValue shouldBe "Text"
        val metadata = text.metadata?.unwrap<SampleTextMetadata>()
        metadata.shouldNotBeNull()
        metadata.text shouldBe "Hello"
        metadata.size shouldBe 16.0
    }

    test("compiles trigger events") {
        val source = """
            use Button@1.0.0
            use Navigate@1.0.0

            Button(title: "Go") {
              OnTap {
                Navigate(to: "Home")
              }
            }
        """.trimIndent()
        val model = defaultCompiler(defaultVersion = NShiftVersion(1, 0, 0)).compile(source)
        model.events.size shouldBe 1
        model.events[0].name.rawValue shouldBe "Navigate"
        model.events[0].trigger.rawValue shouldBe "onTap"
        model.children.isEmpty() shouldBe true
    }

    test("style spacing parameter") {
        val source = """
            use VerticalStack@1.0.0
            use Text@1.0.0

            VerticalStack(style: (spacing: 8)) {
              Text(text: "A", size: 12)
            }
        """.trimIndent()
        val model = defaultCompiler(defaultVersion = NShiftVersion(1, 0, 0)).compile(source)
        model.style.spacing shouldBe 8.0
    }

    test("compile document and compileAll") {
        val source = """
            use Screen@1.0.0
            use Modal@1.0.0

            Screen()
            Modal()
        """.trimIndent()
        val document = RainbowParser().decode(source)
        defaultCompiler().compile(document).name.rawValue shouldBe "Screen"
        defaultCompiler().compileAll(source).map { it.name.rawValue } shouldBe listOf("Screen", "Modal")
        defaultCompiler().compileAll(RainbowDocument()).isEmpty() shouldBe true
    }

    test("throws empty document") {
        shouldThrow<NShiftRainbowCompileError.EmptyDocument> {
            defaultCompiler().compile(RainbowDocument())
        }
    }

    test("throws invalid plugin name") {
        shouldThrow<NShiftRainbowCompileError.InvalidPluginName> {
            defaultCompiler().compile(
                """
                use Screen@1.0.0

                screen()
                """.trimIndent(),
            )
        }.name shouldBe "screen"
    }

    test("throws invalid event name") {
        shouldThrow<NShiftRainbowCompileError.InvalidEventName> {
            defaultCompiler(defaultVersion = NShiftVersion(1, 0, 0)).compile(
                """
                use Button@1.0.0

                Button() {
                  OnTap {
                    show_toast()
                  }
                }
                """.trimIndent(),
            )
        }.name shouldBe "show_toast"
    }

    test("throws invalid slot name") {
        shouldThrow<NShiftRainbowCompileError.InvalidSlotName> {
            defaultCompiler(
                defaultVersion = NShiftVersion(1, 0, 0),
                slotMarkers = setOf("leading"),
            ).compile(
                """
                use Screen@1.0.0
                use Icon@1.0.0

                Screen() {
                  leading {
                    Icon()
                  }
                }
                """.trimIndent(),
            )
        }.name shouldBe "leading"
    }

    test("throws invalid version from use") {
        shouldThrow<NShiftRainbowCompileError.InvalidVersion> {
            defaultCompiler().compile(
                RainbowDocument(
                    uses = listOf(RainbowUseDeclaration("Screen", "1.2")),
                    nodes = listOf(RainbowNode("Screen")),
                ),
            )
        }
    }

    test("throws missing plugin and event version") {
        shouldThrow<NShiftRainbowCompileError.MissingVersion> {
            defaultCompiler().compile("Screen()")
        }.name shouldBe "Screen"
        shouldThrow<NShiftRainbowCompileError.MissingVersion> {
            defaultCompiler().compile(
                """
                use Button@1.0.0

                Button() {
                  OnTap {
                    ShowToast()
                  }
                }
                """.trimIndent(),
            )
        }.name shouldBe "ShowToast"
    }

    test("uses catalog latest when unpinned") {
        val catalog = NShiftRegisteredVersionCatalog.forTesting()
        catalog.registerPlugin("Screen", "1.0.0")
        catalog.registerPlugin("Screen", "2.1.0")
        defaultCompiler(catalog = catalog).compile("Screen()").version.rawValue shouldBe "2.1.0"
    }

    test("uses catalog latest event when unpinned") {
        val catalog = NShiftRegisteredVersionCatalog.forTesting()
        catalog.registerPlugin("Button", "1.0.0")
        catalog.registerEvent("ShowToast", "1.0.0")
        catalog.registerEvent("ShowToast", "3.0.0")
        val model = defaultCompiler(catalog = catalog).compile(
            """
            Button() {
              OnTap {
                ShowToast()
              }
            }
            """.trimIndent(),
        )
        model.events.single().version.rawValue shouldBe "3.0.0"
    }

    test("pin wins over catalog") {
        val catalog = NShiftRegisteredVersionCatalog.forTesting()
        catalog.registerPlugin("Button", "2.0.0")
        defaultCompiler(catalog = catalog).compile(
            """
            use Button@1.0.0

            Button()
            """.trimIndent(),
        ).version.rawValue shouldBe "1.0.0"
    }

    test("catalog wins over default version") {
        val catalog = NShiftRegisteredVersionCatalog.forTesting()
        catalog.registerPlugin("Screen", "2.0.0")
        defaultCompiler(catalog = catalog, defaultVersion = NShiftVersion(1, 0, 0))
            .compile("Screen()")
            .version.rawValue shouldBe "2.0.0"
    }

    test("falls back to default version") {
        defaultCompiler(defaultVersion = NShiftVersion(1, 4, 0))
            .compile("Screen()")
            .version.rawValue shouldBe "1.4.0"
    }

    test("reads id and style spacing double") {
        defaultCompiler().compile(
            """
            use Screen@1.0.0

            Screen(id: "home")
            """.trimIndent(),
        ).id shouldBe "home"
        defaultCompiler().compile(
            """
            use VerticalStack@1.0.0

            VerticalStack(style: (spacing: 8.5))
            """.trimIndent(),
        ).style.spacing shouldBe 8.5
        defaultCompiler().compile(
            """
            use VerticalStack@1.0.0

            VerticalStack(style: 8)
            """.trimIndent(),
        ).style shouldBe NShiftPluginStyle()
    }

    test("compiles nested triggers event slots and multiple events") {
        val nested = defaultCompiler(defaultVersion = NShiftVersion(1, 0, 0)).compile(
            """
            use Button@1.0.0
            use Navigate@1.0.0
            use Track@1.0.0

            Button() {
              OnTap {
                Navigate() {
                  OnAppear {
                    Track()
                  }
                }
              }
            }
            """.trimIndent(),
        )
        nested.events.single().name.rawValue shouldBe "Navigate"
        nested.events.single().trigger.rawValue shouldBe "onTap"
        nested.events.single().events.single().name.rawValue shouldBe "Track"
        nested.events.single().events.single().trigger.rawValue shouldBe "onAppear"

        val withSlot = defaultCompiler(defaultVersion = NShiftVersion(1, 0, 0)).compile(
            """
            use Button@1.0.0
            use Navigate@1.0.0
            use Icon@1.0.0

            Button() {
              OnTap {
                Navigate() {
                  NavLeading {
                    Icon()
                  }
                }
              }
            }
            """.trimIndent(),
        )
        withSlot.events.single().slots[NShiftSlotName("NavLeading")]?.first()?.name?.rawValue shouldBe "Icon"

        val many = defaultCompiler(defaultVersion = NShiftVersion(1, 0, 0)).compile(
            """
            use Button@1.0.0
            use Navigate@1.0.0
            use ShowToast@1.0.0

            Button() {
              OnTap {
                Navigate()
                ShowToast()
              }
            }
            """.trimIndent(),
        )
        many.events.map { it.name.rawValue } shouldBe listOf("Navigate", "ShowToast")
    }

    test("invalid On prefix is a plugin child") {
        val model = defaultCompiler().compile(
            """
            use Screen@1.0.0
            use Ontap@1.0.0

            Screen() {
              Ontap()
            }
            """.trimIndent(),
        )
        model.events.isEmpty() shouldBe true
        model.children.first().name.rawValue shouldBe "Ontap"
    }

    test("metadata decoding failed") {
        val decoder = NShiftRainbowMapMetadataDecoder("Text") { map ->
            SampleTextMetadata(
                text = map["text"] as String,
                size = (map["size"] as Number).toDouble(),
            )
        }
        shouldThrow<NShiftRainbowCompileError.MetadataDecodingFailed> {
            defaultCompiler(defaultVersion = NShiftVersion(1, 0, 0), decoder = decoder).compile(
                """
                use Text@1.0.0

                Text(text: 1, size: "nope")
                """.trimIndent(),
            )
        }.plugin shouldBe "Text"
    }
})
