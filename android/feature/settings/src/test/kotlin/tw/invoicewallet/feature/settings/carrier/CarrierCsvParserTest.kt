package tw.invoicewallet.feature.settings.carrier

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class CarrierCsvParserTest {

    @Test
    fun `parses a typical single-item invoice and converts the YYYYMMDD date`() {
        val result = CarrierCsvParser.parse(SAMPLE_CSV)

        val invoice = result.invoices.first { it.invoiceNumber == "YJ97681309" }
        invoice.issueDate shouldBe LocalDate(2026, 3, 26)
        invoice.totalAmount shouldBe 308
        invoice.sellerTaxId shouldBe "42467936"
        invoice.sellerName shouldBe "新東陽股份有限公司台北國際機場商場門市部"
        invoice.items.map { it.name } shouldBe listOf("大心")
        invoice.items.first().amount shouldBe 308
    }

    @Test
    fun `groups rows that share an invoice number into one invoice with multiple items`() {
        val result = CarrierCsvParser.parse(SAMPLE_CSV)

        val grouped = result.invoices.first { it.invoiceNumber == "YW27987753" }
        grouped.items.map { it.name } shouldBe listOf("通話費", "上網費")
        grouped.items.map { it.amount } shouldBe listOf(400, 209)
        // Header fields come from the first row of the group and aren't duplicated.
        grouped.totalAmount shouldBe 609
    }

    @Test
    fun `skips trailing free-text rows and voided invoices`() {
        val result = CarrierCsvParser.parse(SAMPLE_CSV)

        // 2 trailing notice rows + 1 voided invoice
        result.skippedRows shouldBe 3
        result.invoices.none { it.invoiceNumber == "YV00000099" } shouldBe true
    }

    @Test
    fun `strips the UTF-8 BOM that 財政部's export prepends`() {
        val withBom = "\uFEFF$HEADER_LINE\n手機條碼,20260101,YA00000001,100,開立已確認,否,12345678,測試店,地址,,1,100,100,品項"

        val result = CarrierCsvParser.parse(withBom)

        result.invoices.size shouldBe 1
        result.invoices.first().invoiceNumber shouldBe "YA00000001"
    }

    @Test
    fun `tolerates addresses that happen to contain commas (RFC 4180 quoted field)`() {
        val csv = """
            $HEADER_LINE
            手機條碼,20260102,YB00000002,50,開立已確認,否,87654321,某店,"台北市,信義區xx路1號",,1,50,50,商品
        """.trimIndent()

        val result = CarrierCsvParser.parse(csv)

        val invoice = result.invoices.single()
        invoice.sellerAddress shouldBe "台北市,信義區xx路1號"
        invoice.totalAmount shouldBe 50
    }

    private companion object {
        const val HEADER_LINE =
            "載具自訂名稱,發票日期,發票號碼,發票金額,發票狀態,折讓,賣方統一編號,賣方名稱,賣方地址,買方統編," +
                "消費明細_數量,消費明細_單價,消費明細_金額,消費明細_品名"

        val SAMPLE_CSV = """
            $HEADER_LINE
            手機條碼,20260326,YJ97681309,308,開立已確認,否,42467936,新東陽股份有限公司台北國際機場商場門市部,105台北市松山區敦化北路340之9號,,1,308,308,大心
            手機條碼,20260306,YW27987754,605,開立已確認,否,97176270,台灣大哥大股份有限公司,台北市信義區菸廠路88號12樓,,1,605,605,電信費
            手機條碼,20260306,YW27987753,609,開立已確認,否,97176270,台灣大哥大股份有限公司,台北市信義區菸廠路88號12樓,,1,400,400,通話費
            手機條碼,20260306,YW27987753,609,開立已確認,否,97176270,台灣大哥大股份有限公司,台北市信義區菸廠路88號12樓,,1,209,209,上網費
            手機條碼,20260307,YV00000099,100,開立已作廢,否,11111111,作廢示例,某地址,,1,100,100,作廢品
            捐贈或作廢之發票，字軌號碼均會隱末3碼
            注意：本功能所下載之雲端發票明細檔案可能因賣方營業人後續作廢或折讓等原因而產生誤差。
        """.trimIndent()
    }
}
