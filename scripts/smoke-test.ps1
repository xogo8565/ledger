param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$FrontendUrl = "http://localhost:8081"
)

$ErrorActionPreference = "Stop"

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null
    )

    $uri = "$BaseUrl$Path"
    if ($null -eq $Body) {
        return Invoke-RestMethod -Uri $uri -Method $Method
    }

    return Invoke-RestMethod `
        -Uri $uri `
        -Method $Method `
        -ContentType "application/json" `
        -Body ($Body | ConvertTo-Json -Depth 8)
}

function Invoke-ApiExpectFailure {
    param(
        [string]$Method,
        [string]$Path,
        $Body,
        [int[]]$ExpectedStatusCodes = @(400)
    )

    $uri = "$BaseUrl$Path"
    try {
        Invoke-RestMethod `
            -Uri $uri `
            -Method $Method `
            -ContentType "application/json" `
            -Body ($Body | ConvertTo-Json -Depth 8) | Out-Null
    } catch {
        $statusCode = [int]$_.Exception.Response.StatusCode
        if ($ExpectedStatusCodes -contains $statusCode) {
            return $statusCode
        }
        throw "Expected $Method $Path to fail with $($ExpectedStatusCodes -join '/'), got $statusCode"
    }

    throw "Expected $Method $Path to fail, but it succeeded"
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw "Smoke test failed: $Message"
    }
}

function As-Array {
    param($Value)

    if ($null -eq $Value) {
        return @()
    }
    if ($Value -is [System.Array]) {
        return $Value
    }
    return @($Value)
}

$frontendStatus = (Invoke-WebRequest -Uri $FrontendUrl -UseBasicParsing).StatusCode
Assert-True ($frontendStatus -eq 200) "Frontend did not return HTTP 200"

$bootstrap = Invoke-Api GET "/api/bootstrap"
$expenseCategory = @($bootstrap.categories | Where-Object { $_.type -eq "EXPENSE" } | Select-Object -First 1)[0]
$cashAsset = @($bootstrap.assets | Where-Object { $_.type -ne "CARD" -and $_.type -ne "DEBT" } | Select-Object -First 1)[0]
$cardAsset = @($bootstrap.assets | Where-Object { $_.type -eq "CARD" } | Select-Object -First 1)[0]

Assert-True ($null -ne $expenseCategory) "No expense category is available"
Assert-True ($null -ne $cashAsset) "No cash/bank asset is available"
Assert-True ($null -ne $cardAsset) "No card asset is available"

$invalidCardScheduleStatus = Invoke-ApiExpectFailure POST "/api/cards/$($cardAsset.id)/payment-schedules" @{
    scheduledDate = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
    amount = 0
}

$invalidTransactionStatus = Invoke-ApiExpectFailure POST "/api/transactions" @{
    type = "EXPENSE"
    transactionDate = (Get-Date).ToString("yyyy-MM-dd")
    amount = -100
    categoryId = $expenseCategory.id
    assetId = $cashAsset.id
    title = "invalid smoke transaction"
}

$invalidRecurringStatus = Invoke-ApiExpectFailure POST "/api/recurring-transactions" @{
    type = "EXPENSE"
    amount = 1000
    categoryId = $expenseCategory.id
    assetId = $cashAsset.id
    title = "invalid smoke recurring rule"
    installmentMonths = 0
    frequency = "MONTHLY"
    intervalValue = 1
    startDate = (Get-Date).ToString("yyyy-MM-dd")
    endDate = (Get-Date).AddDays(-1).ToString("yyyy-MM-dd")
    nextRunDate = (Get-Date).ToString("yyyy-MM-dd")
}

$invalidBudgetStatus = Invoke-ApiExpectFailure POST "/api/budgets/settings" @{
    month = (Get-Date).ToString("yyyy-MM")
    totalAmount = -1
    categories = @()
}

$scheduledDate = (Get-Date).AddDays(7).ToString("yyyy-MM-dd")
$cardSchedule = Invoke-Api POST "/api/cards/$($cardAsset.id)/payment-schedules" @{
    scheduledDate = $scheduledDate
    amount = 1234
}
Assert-True ($cardSchedule.status -eq "SCHEDULED") "Card schedule was not created as SCHEDULED"
Invoke-Api DELETE "/api/cards/payment-schedules/$($cardSchedule.id)" | Out-Null
$remainingSchedules = As-Array (Invoke-Api GET "/api/cards/$($cardAsset.id)/payment-schedules")
Assert-True (-not ($remainingSchedules | Where-Object { $_.id -eq $cardSchedule.id })) "Cancelled card schedule still exists"

$recurringRule = Invoke-Api POST "/api/recurring-transactions" @{
    type = "EXPENSE"
    amount = 1000
    categoryId = $expenseCategory.id
    assetId = $cashAsset.id
    title = "smoke recurring rule"
    memo = "temporary smoke test"
    installmentMonths = 0
    frequency = "MONTHLY"
    intervalValue = 1
    startDate = (Get-Date).ToString("yyyy-MM-dd")
    nextRunDate = (Get-Date).AddDays(7).ToString("yyyy-MM-dd")
}
Assert-True ($null -ne $recurringRule.id) "Recurring rule was not created"
Invoke-Api DELETE "/api/recurring-transactions/$($recurringRule.id)" | Out-Null
$activeRules = As-Array (Invoke-Api GET "/api/recurring-transactions")
Assert-True (-not ($activeRules | Where-Object { $_.id -eq $recurringRule.id })) "Deleted recurring rule is still active"

$installment = Invoke-Api POST "/api/transactions" @{
    type = "EXPENSE"
    transactionDate = (Get-Date).ToString("yyyy-MM-dd")
    amount = 3
    categoryId = $expenseCategory.id
    assetId = $cashAsset.id
    title = "smoke installment"
    memo = "temporary smoke test"
    installmentMonths = 3
}
Assert-True ($installment.installmentMonths -eq 3) "Installment transaction did not keep installmentMonths"
Assert-True (-not [string]::IsNullOrWhiteSpace($installment.installmentGroupId)) "Installment group id was not created"

$installmentRows = As-Array (Invoke-Api GET "/api/transactions/installments/$($installment.installmentGroupId)")
$installmentTotal = [decimal]0
foreach ($row in $installmentRows) {
    $installmentTotal += [decimal]$row.amount
}
Assert-True ($installmentRows.Count -eq 3) "Installment schedule did not contain three rows"
Assert-True ([decimal]$installmentTotal -eq 3) "Installment total did not match original amount"

foreach ($row in $installmentRows) {
    Invoke-Api DELETE "/api/transactions/$($row.id)" | Out-Null
}

[pscustomobject]@{
    frontendStatus = $frontendStatus
    cardScheduleCreateCancel = "ok"
    recurringRuleCreateDelete = "ok"
    installmentCreateScheduleDelete = "ok"
    installmentRows = $installmentRows.Count
    invalidCardScheduleStatus = $invalidCardScheduleStatus
    invalidTransactionStatus = $invalidTransactionStatus
    invalidRecurringStatus = $invalidRecurringStatus
    invalidBudgetStatus = $invalidBudgetStatus
} | ConvertTo-Json
