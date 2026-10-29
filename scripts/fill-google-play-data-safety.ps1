param(
    [Parameter(Mandatory = $true)]
    [string]$InputCsv,

    [Parameter(Mandatory = $true)]
    [string]$OutputCsv
)

$rows = Import-Csv -LiteralPath $InputCsv

foreach ($row in $rows) {
    $row.'Response value' = ''
}

function Set-Answer {
    param(
        [Parameter(Mandatory = $true)]
        [string]$QuestionId,

        [AllowEmptyString()]
        [string]$ResponseId = '',

        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    $matches = @($rows | Where-Object {
        $_.'Question ID (machine readable)' -eq $QuestionId -and
        $_.'Response ID (machine readable)' -eq $ResponseId
    })

    if ($matches.Count -ne 1) {
        throw "Expected one row for '$QuestionId/$ResponseId', found $($matches.Count)."
    }

    $matches[0].'Response value' = $Value
}

# General collection and security declarations.
Set-Answer 'PSL_DATA_COLLECTION_COLLECTS_PERSONAL_DATA' '' 'TRUE'
Set-Answer 'PSL_DATA_COLLECTION_ENCRYPTED_IN_TRANSIT' '' 'TRUE'
Set-Answer 'PSL_SUPPORTED_ACCOUNT_CREATION_METHODS' 'PSL_ACM_NONE' 'TRUE'
Set-Answer 'PSL_SUPPORT_DATA_DELETION_BY_USER' 'DATA_DELETION_YES' 'TRUE'
Set-Answer 'PSL_DATA_DELETION_URL' '' 'https://slavafit.github.io/OrderPackager/privacy-policy.html'

# Accounts are provisioned by the developer in Supabase Auth, outside the app.
Set-Answer 'PSL_HAS_OUTSIDE_APP_ACCOUNTS' '' 'TRUE'
Set-Answer 'PSL_OUTSIDE_APP_ACCOUNT_TYPES' 'PSL_OUTSIDE_APP_ACCOUNT_TYPE_OTHER' 'TRUE'
Set-Answer 'PSL_OUTSIDE_APP_ACCOUNT_TYPE_SPECIFY' '' 'Аккаунт создается разработчиком вне приложения в Supabase Auth.'

$dataTypes = @(
    @{
        Group = 'PSL_DATA_TYPES_PERSONAL'
        Type = 'PSL_NAME'
        Purposes = @('PSL_APP_FUNCTIONALITY')
    },
    @{
        Group = 'PSL_DATA_TYPES_PERSONAL'
        Type = 'PSL_EMAIL'
        Purposes = @('PSL_APP_FUNCTIONALITY', 'PSL_ACCOUNT_MANAGEMENT')
    },
    @{
        Group = 'PSL_DATA_TYPES_PERSONAL'
        Type = 'PSL_USER_ACCOUNT'
        Purposes = @('PSL_APP_FUNCTIONALITY', 'PSL_ACCOUNT_MANAGEMENT')
    },
    @{
        Group = 'PSL_DATA_TYPES_APP_ACTIVITY'
        Type = 'PSL_USER_GENERATED_CONTENT'
        Purposes = @('PSL_APP_FUNCTIONALITY')
    }
)

foreach ($dataType in $dataTypes) {
    Set-Answer $dataType.Group $dataType.Type 'TRUE'

    $usagePrefix = "PSL_DATA_USAGE_RESPONSES:$($dataType.Type):"
    Set-Answer "${usagePrefix}PSL_DATA_USAGE_COLLECTION_AND_SHARING" 'PSL_DATA_USAGE_ONLY_COLLECTED' 'TRUE'
    Set-Answer "${usagePrefix}PSL_DATA_USAGE_EPHEMERAL" '' 'FALSE'
    Set-Answer "${usagePrefix}DATA_USAGE_USER_CONTROL" 'PSL_DATA_USAGE_USER_CONTROL_REQUIRED' 'TRUE'

    foreach ($purpose in $dataType.Purposes) {
        Set-Answer "${usagePrefix}DATA_USAGE_COLLECTION_PURPOSE" $purpose 'TRUE'
    }
}

$outputDirectory = Split-Path -Parent $OutputCsv
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

$rows | Export-Csv -LiteralPath $OutputCsv -NoTypeInformation -Encoding utf8

Write-Host "Created $OutputCsv"
