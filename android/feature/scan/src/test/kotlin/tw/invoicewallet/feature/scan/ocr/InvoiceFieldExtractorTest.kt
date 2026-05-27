package tw.invoicewallet.feature.scan.ocr

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class InvoiceFieldExtractorTest {

    private val extractor = InvoiceFieldExtractor()

    @Test
    fun `extracts number, ROC date and total from an e-invoice proof layout`() {
        val fields = extractor.extract(
            RecognizedText.of(
                "財政部電子發票證明聯",
                "ZF-76011229",
                "開立日期 115/04/17",
                "七里香甕仔雞",
                "總計 1080",
            ),
        )

        fields.invoiceNumber?.value shouldBe "ZF76011229"
        fields.issueDate?.value shouldBe LocalDate(2026, 4, 17)
        fields.totalAmount?.value shouldBe 1080
    }

    @Test
    fun `handles a cash-register receipt with a Gregorian date, 合計 total and no invoice number`() {
        val fields = extractor.extract(RecognizedText.of("家樂福", "2026/04/21", "合計 232"))

        fields.invoiceNumber shouldBe null
        fields.issueDate?.value shouldBe LocalDate(2026, 4, 21)
        fields.totalAmount?.value shouldBe 232
        // 合計 is a weaker signal than 總計 → flagged for the user to double-check.
        fields.lowConfidenceFields() shouldContain InvoiceField.TOTAL_AMOUNT
    }

    @Test
    fun `recognises the ROC CJK date format 民國年月日`() {
        extractor.extract(RecognizedText.of("民國115年04月17日")).issueDate?.value shouldBe LocalDate(2026, 4, 17)
    }

    @Test
    fun `recognises a dotted Gregorian date`() {
        extractor.extract(RecognizedText.of("2026.04.26")).issueDate?.value shouldBe LocalDate(2026, 4, 26)
    }

    @Test
    fun `recognises a dash-separated ROC date`() {
        extractor.extract(RecognizedText.of("115-04-21")).issueDate?.value shouldBe LocalDate(2026, 4, 21)
    }

    @Test
    fun `parses an amount written with a thousands separator`() {
        extractor.extract(RecognizedText.of("總計 1,080")).totalAmount?.value shouldBe 1080
    }

    @Test
    fun `total keyword 總計 wins over 合計 when both appear`() {
        extractor.extract(RecognizedText.of("合計 1000", "總計 1080")).totalAmount?.value shouldBe 1080
    }

    @Test
    fun `flags a fused invoice number without separator as low confidence`() {
        val fields = extractor.extract(RecognizedText.of("YZ29863957"))

        fields.invoiceNumber?.value shouldBe "YZ29863957"
        fields.lowConfidenceFields() shouldContain InvoiceField.INVOICE_NUMBER
    }

    @Test
    fun `returns nulls when nothing matches`() {
        val fields = extractor.extract(RecognizedText.of("謝謝光臨", "歡迎再來"))

        fields.invoiceNumber shouldBe null
        fields.issueDate shouldBe null
        fields.totalAmount shouldBe null
    }
}
