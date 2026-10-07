// ============================================================
// FILE: app/src/main/java/com/nischa/intentionalspending/Data.kt
// ============================================================
package com.nischa.intentionalspending

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/** A single line item: "Groceries — 12,000" */
data class Item(val id: Long, val name: String, val amount: Double)

/** The three buckets of the 50/30/20 rule. */
enum class Bucket(val label: String, val goalPct: Double) {
    NEEDS("Needs", 0.50),
    SAVINGS("Savings & Investments", 0.20),
    WANTS("Wants", 0.30);

    /** Mirrors the "Available to spend / Overspent" logic in the spreadsheet. */
    fun verdict(diff: Double): String = when (this) {
        SAVINGS ->
            if (diff >= 0) "left to allocate ${pkr(diff)}"
            else "you beat your goal by ${pkr(-diff)}"
        else ->
            if (diff >= 0) "available to spend ${pkr(diff)}"
            else "overspent by ${pkr(-diff)}"
    }
}

/** Whole-app state. */
data class BudgetState(
    val income: List<Item> = emptyList(),
    val needs: List<Item> = emptyList(),
    val savings: List<Item> = emptyList(),
    val wants: List<Item> = emptyList()
) {
    val totalIncome get() = income.sumOf { it.amount }
    val totalNeeds get() = needs.sumOf { it.amount }
    val totalSavings get() = savings.sumOf { it.amount }
    val totalWants get() = wants.sumOf { it.amount }
    val totalSpend get() = totalNeeds + totalSavings + totalWants
    val unallocated get() = totalIncome - totalSpend

    fun listFor(bucket: Bucket) = when (bucket) {
        Bucket.NEEDS -> needs
        Bucket.SAVINGS -> savings
        Bucket.WANTS -> wants
    }

    fun withList(bucket: Bucket, list: List<Item>) = when (bucket) {
        Bucket.NEEDS -> copy(needs = list)
        Bucket.SAVINGS -> copy(savings = list)
        Bucket.WANTS -> copy(wants = list)
    }

    fun totalFor(bucket: Bucket) = listFor(bucket).sumOf { it.amount }
}

// ------------------------------------------------------------
// Currency helpers — Pakistani Rupees
// ------------------------------------------------------------
private val pkrFormat: NumberFormat =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("en-PK")).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

private val pkrFormatDecimals: NumberFormat =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("en-PK")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }

/** "Rs 12,500" */
fun pkr(value: Double): String =
    "Rs " + (if (value % 1.0 == 0.0) pkrFormat else pkrFormatDecimals).format(value)

/** "42.3%" */
fun pctText(fraction: Double): String = String.format(Locale.US, "%.1f%%", fraction * 100)

// ------------------------------------------------------------
// Persistence — everything stays on the phone, no internet needed.
// ------------------------------------------------------------
class Store(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("intentional_spending", Context.MODE_PRIVATE)

    fun load(): BudgetState {
        val raw = prefs.getString(KEY_STATE, null) ?: return BudgetState()
        return try {
            val json = JSONObject(raw)
            BudgetState(
                income = decodeList(json.optJSONArray("income")),
                needs = decodeList(json.optJSONArray("needs")),
                savings = decodeList(json.optJSONArray("savings")),
                wants = decodeList(json.optJSONArray("wants"))
            )
        } catch (e: Exception) {
            BudgetState()
        }
    }

    fun save(state: BudgetState) {
        val json = JSONObject().apply {
            put("income", encodeList(state.income))
            put("needs", encodeList(state.needs))
            put("savings", encodeList(state.savings))
            put("wants", encodeList(state.wants))
        }
        prefs.edit().putString(KEY_STATE, json.toString()).apply()
    }

    private fun encodeList(list: List<Item>): JSONArray {
        val array = JSONArray()
        list.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("amount", item.amount)
                }
            )
        }
        return array
    }

    private fun decodeList(array: JSONArray?): List<Item> {
        if (array == null) return emptyList()
        val out = ArrayList<Item>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            out.add(
                Item(
                    id = obj.optLong("id", System.currentTimeMillis() + i),
                    name = obj.optString("name", ""),
                    amount = obj.optDouble("amount", 0.0)
                )
            )
        }
        return out
    }

    private companion object {
        const val KEY_STATE = "state_v1"
    }
}
