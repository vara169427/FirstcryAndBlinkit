# deploy-ec2-scp.ps1
# Automates local Docker build, export, SCP upload, and remote deployment on AWS EC2.

param (
    [Parameter(Mandatory=$true)]
    [string]$EC2Ip,

    [Parameter(Mandatory=$true)]
    [string]$KeyPath,

    [Parameter(Mandatory=$false)]
    [string]$Username = "ubuntu"
)

$ErrorActionPreference = "Stop"

# Define local filenames
$ImageName = "stock-checker"
$TarFile = "stock-checker.tar"
$RemotePath = "$Username@$EC2Ip"

# Step 0: Ensure KeyPath is correctly formatted and accessible
if (-not (Test-Path $KeyPath)) {
    Write-Error "Private key file not found at path: $KeyPath"
}

# Resolve paths to absolute
$ResolvedKeyPath = (Resolve-Path $KeyPath).Path
Write-Host "Using SSH Private Key: $ResolvedKeyPath" -ForegroundColor Cyan

# Step 1: Build Docker image locally
Write-Host "`n=== Step 1: Building local Docker image '$ImageName' ===" -ForegroundColor Cyan
# Run docker build in the root directory (parent of deployment/)
Push-Location "$PSScriptRoot\.."
try {
    docker build -t $ImageName .
} finally {
    Pop-Location
}

# Step 2: Save Docker image to tar archive
Write-Host "`n=== Step 2: Saving Docker image to '$TarFile' ===" -ForegroundColor Cyan
if (Test-Path "$PSScriptRoot\..\$TarFile") {
    Write-Host "Removing old $TarFile..." -ForegroundColor Yellow
    Remove-Item "$PSScriptRoot\..\$TarFile"
}
docker save -o "$PSScriptRoot\..\$TarFile" $ImageName

# Step 3: Copy tar archive to EC2 instance using SCP
Write-Host "`n=== Step 3: Transferring '$TarFile' to EC2 ($EC2Ip) via SCP ===" -ForegroundColor Cyan
Write-Host "This might take a couple of minutes depending on your upload speed..." -ForegroundColor Yellow
scp -i "$ResolvedKeyPath" -o StrictHostKeyChecking=no "$PSScriptRoot\..\$TarFile" "$RemotePath`:~/$TarFile"

# Step 4: Clean up local tar file
Write-Host "`n=== Step 4: Cleaning up local tar file ===" -ForegroundColor Cyan
Remove-Item "$PSScriptRoot\..\$TarFile"

# Step 5: Execute remote commands on EC2 via SSH
Write-Host "`n=== Step 5: Loading image and running container on EC2 ===" -ForegroundColor Cyan

$RemoteCommands = @"
echo 'Stopping and removing existing stock-checker container (if any)...'
docker stop stock-checker 2>/dev/null || true
docker rm stock-checker 2>/dev/null || true

echo 'Loading Docker image from tar archive...'
docker load -i ~/$TarFile

echo 'Removing remote tar archive to save space...'
rm ~/$TarFile

echo 'Running stock-checker container on port 9091...'
docker run -d --name stock-checker --restart always -p 9091:9091 stock-checker

echo 'Status of running containers:'
docker ps
"@

ssh -i "$ResolvedKeyPath" -o StrictHostKeyChecking=no "$RemotePath" "$RemoteCommands"

Write-Host "`n========================================================" -ForegroundColor Green
Write-Host "Deployment completed successfully!" -ForegroundColor Green
Write-Host "You can access your application at: http://$EC2Ip:9091" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
