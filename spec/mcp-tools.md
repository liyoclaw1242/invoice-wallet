# MCP Tools — Invoice Wallet (in-app local server)

對外暴露的 MCP tools，由 App 內 Ktor server 提供（`POST /mcp`，JSON-RPC 2.0，Bearer token）。
所有 tool 都在 `AuthorizationEngine` 的約束下執行：日期窗（`dateRangeDays`）、排除類別
（`excludedCategories`）、per-grant rate limit；每次成功呼叫寫入 `QueryAuditLog`。

結果以 MCP `content` text block 回傳，`text` 內含本文件描述的 JSON。投影**不含**加密載具碼。

## list_invoices

依條件列出發票並分頁。

| 參數 | 型別 | 說明 |
|---|---|---|
| date_from | string (ISO date) | 起始日期（含） |
| date_to | string (ISO date) | 結束日期（含） |
| merchant_name | string | 商店名稱子字串比對 |
| category | string | 類別精確比對 |
| min_amount | integer | 最低總金額 |
| max_amount | integer | 最高總金額 |
| limit | integer | 回傳上限（預設 50，最多 500） |
| offset | integer | 略過筆數（預設 0） |

回傳：`{ "total": <符合總數>, "items": [ <invoice> ] }`

## get_invoice_detail

| 參數 | 型別 | 必填 | 說明 |
|---|---|---|---|
| invoice_id | string | ✓ | 發票 id |

回傳：單一 `<invoice>`；找不到或被授權約束擋下時為 `null`。

## search_invoices

| 參數 | 型別 | 必填 | 說明 |
|---|---|---|---|
| keyword | string | ✓ | 比對商店名／發票號碼／備註 |
| limit | integer | | 回傳上限（預設 50，最多 500） |

回傳：`{ "items": [ <invoice> ] }`

## get_spending_summary

| 參數 | 型別 | 說明 |
|---|---|---|
| date_from | string (ISO date) | 起始日期（含） |
| date_to | string (ISO date) | 結束日期（含） |
| group_by | string | `category` \| `merchant` \| `month` \| `weekday`（預設 category） |

回傳：`{ "group_by", "invoice_count", "total_amount", "groups": [ { "key", "total_amount", "count" } ] }`

## get_lottery_status

| 參數 | 型別 | 說明 |
|---|---|---|
| period | string | 期別 yyymm，如 `11502`（選填） |

回傳：`{ "period"?, "invoice_count", "status_counts": { PENDING, CHECKED_NO_PRIZE, CHECKED_WON, CLAIMED }, "total_winnings", "winners": [ { "invoice_number", "prize", "status" } ] }`

## list_merchants

| 參數 | 型別 | 說明 |
|---|---|---|
| min_visits | integer | 最少造訪次數（預設 1） |
| date_from | string (ISO date) | 起始日期（含） |
| limit | integer | 回傳上限（預設 50） |

回傳：`{ "items": [ { "merchant_name", "merchant_tax_id"?, "visit_count", "total_amount" } ] }`

## `<invoice>` 投影

```json
{
  "id", "invoice_number", "issue_date", "issue_period",
  "merchant_name", "merchant_tax_id"?,
  "total_amount", "tax_amount", "category"?,
  "lottery_status", "lottery_prize"?, "note"?, "tags"?
}
```
