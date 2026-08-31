$ErrorActionPreference = 'Stop'
$port = if ($args.Count -gt 0) { $args[0] } else { '8001' }
java -jar (Join-Path $PSScriptRoot 'donation-service.jar') $port
