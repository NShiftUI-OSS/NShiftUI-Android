package com.nshiftui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NShiftUIVersionTests : FunSpec({
    test("reports library version") {
        NShiftUIVersion.current shouldBe "1.0.0"
    }
})
