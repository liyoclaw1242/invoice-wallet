package tw.invoicewallet.core.designsystem.components

import androidx.annotation.DrawableRes
import tw.invoicewallet.core.designsystem.R

/**
 * Heuristic merchant-name → category slug. Slugs match `ic_category_*` drawable names so
 * the chip/icon can be looked up in one step. Returns null when nothing matches — the
 * UI then shows the invoice under "全部" only.
 *
 * Rules are ordered most-specific-first; a `加油站` chain ending in 「便利商店」 should
 * still be classified as transit, not convstore.
 */
object CategoryGuesser {

    val ALL_SLUGS: List<String> = listOf(
        "food",
        "drink",
        "convstore",
        "tech",
        "medical",
        "transit",
        "clothing",
        "home",
    )

    fun guess(merchantName: String): String? {
        if (merchantName.isBlank()) return null
        for ((re, slug) in RULES) {
            if (re.containsMatchIn(merchantName)) return slug
        }
        return null
    }

    fun label(slug: String): String = when (slug) {
        "food" -> "食"
        "drink" -> "飲"
        "convstore" -> "超商"
        "tech" -> "3C"
        "medical" -> "醫療"
        "transit" -> "交通"
        "clothing" -> "服飾"
        "home" -> "居家"
        else -> slug
    }

    @DrawableRes
    fun iconRes(slug: String): Int? = when (slug) {
        "food" -> R.drawable.ic_category_food
        "drink" -> R.drawable.ic_category_drink
        "convstore" -> R.drawable.ic_category_convstore
        "tech" -> R.drawable.ic_category_tech
        "medical" -> R.drawable.ic_category_medical
        "transit" -> R.drawable.ic_category_transit
        "clothing" -> R.drawable.ic_category_clothing
        "home" -> R.drawable.ic_category_home
        else -> null
    }

    // Order matters — more specific first. Each rule is one Regex with `|` alternatives.
    private val RULES: List<Pair<Regex, String>> = listOf(
        Regex("加油|福懋|中油|台塑石油|高鐵|台鐵|捷運|計程|統聯|國光|和欣|油站|Uber|租車") to "transit",
        Regex("醫院|診所|藥局|健保|杏一|大樹|耳鼻喉|牙科|眼科") to "medical",
        Regex("燦坤|全國電子|神腦|順發|Apple|蘋果|NOVA|三創|光華|燦星|青蘋果") to "tech",
        Regex("IKEA|特力屋|B&Q|家樂福|大潤發|愛買|五金|居家|生活百貨") to "home",
        Regex("UNIQLO|ZARA|GU|NET|GAP|服飾|時尚|鞋店|H&M") to "clothing",
        Regex("咖啡|拿鐵|奶茶|紅茶|綠茶|烏龍|飲料|星巴克|Starbucks|路易莎|85度C|五十嵐|COMEBUY|清心|龜記") to "drink",
        Regex("7-ELEVEN|7-Eleven|統一超商|全家便利|萊爾富|OK.{0,3}便利|全聯|福利中心|美廉社|頂好") to "convstore",
        Regex("麥當勞|肯德基|KFC|漢堡|拉麵|牛肉麵|火鍋|餐廳|燒肉|排骨|雞肉|早午餐|便當|食堂|美食|麥味登|滿分堡|鼎泰豐|新東陽|魯魯") to "food",
    )
}
