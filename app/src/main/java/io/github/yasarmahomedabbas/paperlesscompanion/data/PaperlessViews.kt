package io.github.yasarmahomedabbas.paperlesscompanion.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// A saved view's filter rules, turned into the query parameters Paperless's own web interface sends for it.

/** One of a saved view's `filter_rules`: its `rule_type` and `value` (always a string or null). */
internal data class ViewRule(val type: Int, val value: String?)

/** What a saved view asks of `api/documents/`: its filter [params] and its [ordering]. */
internal data class ViewFilter(val params: Map<String, String>, val ordering: String)

/**
 * The parameters the web interface sends for [rules], rule for rule as `queryParamsFromFilterRules`
 * (`src-ui/src/app/utils/query-params.ts`) over the table in `src-ui/src/app/data/filter-rule-type.ts`,
 * both at Paperless-ngx v3.3.4. A rule type that table doesn't know is dropped, and so is a null value
 * the web interface would leave out of the request.
 */
internal fun viewFilter(rules: List<ViewRule>, sortField: String?, sortReverse: Boolean): ViewFilter {
    val params = LinkedHashMap<String, String>()
    for (rule in rules) {
        val type = RULE_TYPES[rule.type] ?: continue
        val value = rule.value
        when {
            rule.type == TITLE_CONTENT || rule.type == SIMPLE_TEXT -> value?.let { params["text"] = it }
            rule.type == TITLE || rule.type == SIMPLE_TITLE -> value?.let { params["title_search"] = it }
            type.isnullFiltervar != null && value == null -> params[type.isnullFiltervar] = "1"
            type.isnullFiltervar != null && value == NEGATIVE_NULL -> params[type.isnullFiltervar] = "0"
            type.boolean -> params[type.filtervar] = if (value == "true" || value == "1") "1" else "0"
            value == null -> Unit
            type.multi -> params[type.filtervar] = params[type.filtervar]?.let { "$it,$value" } ?: value
            else -> params[type.filtervar] = value
        }
    }
    return ViewFilter(params, (if (sortReverse) "-" else "") + (sortField ?: "created"))
}

/** A saved view's `filter_rules`; a number value is read as its digits, a missing or odd one as null. */
internal fun JsonObject.toViewRules(): List<ViewRule> =
    (this["filter_rules"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { rule ->
        val type = rule.int("rule_type") ?: return@mapNotNull null
        val value = (rule["value"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
        ViewRule(type, value)
    }

/** A saved view's filter: its rules, `sort_field` and `sort_reverse`. */
internal fun JsonObject.toViewFilter(): ViewFilter = viewFilter(
    toViewRules(),
    string("sort_field")?.takeIf { it.isNotBlank() },
    (this["sort_reverse"] as? JsonPrimitive)?.contentOrNull == "true",
)

private const val TITLE = 0
private const val TITLE_CONTENT = 19
private const val SIMPLE_TITLE = 48
private const val SIMPLE_TEXT = 49

/** NEGATIVE_NULL_FILTER_VALUE: "has none of these is false", i.e. the field is set. */
private const val NEGATIVE_NULL = "-1"

private class RuleType(
    val filtervar: String,
    val isnullFiltervar: String? = null,
    val multi: Boolean = false,
    val boolean: Boolean = false,
)

/** `FILTER_RULE_TYPES` at v3.3.4, by id. */
private val RULE_TYPES: Map<Int, RuleType> = mapOf(
    0 to RuleType("title__icontains"),
    48 to RuleType("title_search"),
    1 to RuleType("content__icontains"),
    2 to RuleType("archive_serial_number"),
    3 to RuleType("correspondent__id", isnullFiltervar = "correspondent__isnull"),
    26 to RuleType("correspondent__id__in", multi = true),
    27 to RuleType("correspondent__id__none", multi = true),
    25 to RuleType("storage_path__id", isnullFiltervar = "storage_path__isnull"),
    30 to RuleType("storage_path__id__in", multi = true),
    31 to RuleType("storage_path__id__none", multi = true),
    4 to RuleType("document_type__id", isnullFiltervar = "document_type__isnull"),
    28 to RuleType("document_type__id__in", multi = true),
    29 to RuleType("document_type__id__none", multi = true),
    5 to RuleType("is_in_inbox", boolean = true),
    6 to RuleType("tags__id__all", multi = true),
    22 to RuleType("tags__id__in", multi = true),
    17 to RuleType("tags__id__none", multi = true),
    7 to RuleType("is_tagged", boolean = true),
    8 to RuleType("created__date__lt"),
    9 to RuleType("created__date__gt"),
    43 to RuleType("created__date__lte"),
    44 to RuleType("created__date__gte"),
    10 to RuleType("created__year"),
    11 to RuleType("created__month"),
    12 to RuleType("created__day"),
    13 to RuleType("added__date__lt"),
    14 to RuleType("added__date__gt"),
    45 to RuleType("added__date__lte"),
    46 to RuleType("added__date__gte"),
    15 to RuleType("modified__date__lt"),
    16 to RuleType("modified__date__gt"),
    18 to RuleType("archive_serial_number__isnull", boolean = true),
    23 to RuleType("archive_serial_number__gt"),
    24 to RuleType("archive_serial_number__lt"),
    19 to RuleType("title_content"),
    49 to RuleType("text"),
    20 to RuleType("query"),
    21 to RuleType("more_like_id"),
    32 to RuleType("owner__id"),
    33 to RuleType("owner__id__in", multi = true),
    34 to RuleType("owner__isnull", boolean = true),
    35 to RuleType("owner__id__none", multi = true),
    37 to RuleType("shared_by__id", multi = true),
    36 to RuleType("custom_fields__icontains"),
    38 to RuleType("custom_fields__id__all", multi = true),
    39 to RuleType("custom_fields__id__in", multi = true),
    40 to RuleType("custom_fields__id__none", multi = true),
    41 to RuleType("has_custom_fields", boolean = true),
    42 to RuleType("custom_field_query"),
    47 to RuleType("mime_type"),
    50 to RuleType("has_duplicates", boolean = true),
)
