# This script is used to start the chat server and handle restarts based on exit codes.
$jarPath = 'server\target\chat-server.jar'
$firstRun = $true

# Check if the JAR file exists.
while ($true) {
    $command = 'java -jar "' + $jarPath + '"'

    # If this is not the first run, add the --auto-start flag.
    if (-not $firstRun) {
        $command += ' --auto-start'
    }
    
    # Invoke the command to start the server.
    Write-Host "Running $command..."
    Invoke-Expression $command

    # Capture the exit code.
    $exitCode = $LASTEXITCODE

    # If the exit code is 5, it indicates a restart signal from the server.
    if ($exitCode -eq 5) {
        Write-Host 'Detected restart signal from server. Restarting...'
        Start-Sleep -Seconds 5
        $firstRun = $false  
        continue
    } else {
        Write-Host "Detected normal exit code ($exitCode). Stopping server."
        break
    }
}
