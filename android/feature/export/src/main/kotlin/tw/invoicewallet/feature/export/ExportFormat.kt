package tw.invoicewallet.feature.export

/** A user-facing export format, with the metadata the Storage Access Framework needs. */
enum class ExportFormat(val extension: String, val mimeType: String) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
}
