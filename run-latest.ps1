$jar = Get-ChildItem -Path "$PSScriptRoot\jars" -Filter "*.jar" |
    Sort-Object LastWriteTime |
    Select-Object -Last 1

if (-not $jar) {
    Write-Error "No jar files found in jars/"
    exit 1
}

Write-Host "Running $($jar.Name)"
& java -ea -jar $jar.FullName
