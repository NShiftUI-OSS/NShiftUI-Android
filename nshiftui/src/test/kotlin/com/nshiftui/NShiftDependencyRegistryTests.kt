package com.nshiftui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private data class TestClient(val value: String)

class NShiftDependencyRegistryTests : FunSpec({
    test("resolves registered dependency by type") {
        val registry = NShiftDependencyRegistry()
        registry.register<TestClient> { TestClient("default") }
        registry.resolve<TestClient>() shouldBe TestClient("default")
    }

    test("resolves registered dependency by name") {
        val registry = NShiftDependencyRegistry()
        registry.register<TestClient>(name = "primary") { TestClient("primary") }
        registry.register<TestClient>(name = "secondary") { TestClient("secondary") }
        registry.resolve<TestClient>("primary") shouldBe TestClient("primary")
        registry.resolve<TestClient>("secondary") shouldBe TestClient("secondary")
    }

    test("returns nil for missing dependency") {
        val registry = NShiftDependencyRegistry()
        registry.resolve<TestClient>("missing").shouldBeNull()
    }

    test("resolves dependency with arguments") {
        val registry = NShiftDependencyRegistry()
        registry.register<TestClient, String, String>(name = "request") { method, url ->
            TestClient("$method $url")
        }
        registry.resolve<TestClient>("request", "POST", "/login") shouldBe TestClient("POST /login")
    }

    test("resolves same name with different argument signatures") {
        val registry = NShiftDependencyRegistry()
        registry.register<TestClient, String>(name = "client") { baseURL ->
            TestClient(baseURL)
        }
        registry.register<TestClient, String, String>(name = "client") { baseURL, token ->
            TestClient("$baseURL:$token")
        }
        registry.resolve<TestClient>("client", "https://api") shouldBe TestClient("https://api")
        registry.resolve<TestClient>("client", "https://api", "token") shouldBe TestClient("https://api:token")
    }

    test("singleton caches instance") {
        val registry = NShiftDependencyRegistry()
        var count = 0
        registry.register<TestClient>(scope = NShiftDependencyScope.Singleton) {
            count += 1
            TestClient("once")
        }
        registry.resolve<TestClient>() shouldBe TestClient("once")
        registry.resolve<TestClient>() shouldBe TestClient("once")
        count shouldBe 1
    }
})
