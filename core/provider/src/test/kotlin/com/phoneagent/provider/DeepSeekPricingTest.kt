package com.phoneagent.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekPricingTest {
    @Test
    fun `deepseek preset follows official CNY rate card`() {
        val pricing = ProviderPresets.all.first { it.id == "deepseek" }.modelPricing
        assertEquals(ModelPricing(0.02, 1.0, 2.0, "DeepSeek 官方中国区价表", "CNY"), pricing["deepseek-v4-flash"])
        assertEquals(ModelPricing(0.025, 3.0, 6.0, "DeepSeek 官方中国区价表", "CNY"), pricing["deepseek-v4-pro"])
    }
}
