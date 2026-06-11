# AWS EC2 Deployment Guide: Stock Checker

This guide provides step-by-step instructions for provisioning your AWS EC2 instance and deploying the **Stock Checker** web application.

---

## 🛠️ Prerequisites

Before you begin, ensure you have:
1. An active **AWS Account**.
2. An **EC2 Key Pair** (PEM format) downloaded locally to access the EC2 instance via SSH.
3. **Docker** installed on your local machine (if using the local build/PowerShell script method).

---

## 🚀 Step 1: Provision Infrastructure on AWS

You can provision your infrastructure manually or using the provided CloudFormation template.

### Option A: Using AWS CloudFormation (Automated & Recommended)

1. Log in to the [AWS Management Console](https://console.aws.amazon.com/).
2. Navigate to **CloudFormation** and click **Create stack** -> **With new resources (standard)**.
3. Choose **Upload a template file** and upload the [aws-infrastructure.yaml](aws-infrastructure.yaml) template.
4. Fill in the parameters:
   - **Stack name**: `stock-checker-stack`
   - **KeyName**: Select your existing EC2 SSH Key Pair.
   - **InstanceType**: We recommend `t3.small` or `t3.medium` because Playwright runs a resource-intensive Chromium browser.
   - **SSHLocation**: Restrict to your IP (e.g. `192.0.2.0/24`) or leave as `0.0.0.0/0` for global access.
5. Click **Next**, keep defaults, and click **Submit**.
6. Wait for the stack status to become `CREATE_COMPLETE`. Check the **Outputs** tab to get your **Public IP** and **AppURL**.

---

### Option B: Manual Setup via AWS Console

If you prefer to configure the EC2 instance manually:
1. Go to the **EC2 Dashboard** and click **Launch instance**.
2. Set Name to `Stock-Checker-Host`.
3. Choose **Ubuntu 22.04 LTS** (AMI) or **Amazon Linux 2023**.
4. Choose **Instance Type**: Select `t3.small` or `t3.medium`.
5. Select your **Key pair**.
6. Under **Network settings**, create a security group:
   - Allow **SSH** (Port 22) from your IP.
   - Add a custom TCP rule: Allow Port **9091** from Anywhere (`0.0.0.0/0`).
7. Under **Configure storage**, change the size to **20 GiB** (SSD gp3).
8. Under **Advanced details**, expand and paste the following script into the **User data** box to auto-install Docker:
   ```bash
   #!/bin/bash
   apt-get update -y
   apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release
   mkdir -p /etc/apt/keyrings
   curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
   echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
   apt-get update -y
   apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
   systemctl start docker
   systemctl enable docker
   usermod -aG docker ubuntu
   ```
9. Click **Launch instance**.

---

## 📦 Step 2: Deploy the Application

Choose one of the following deployment strategies to get the application onto your EC2 instance.

### Strategy 1: Using PowerShell Automation Script (Easiest Local Deploy)

We have provided a script [deploy-ec2-scp.ps1](deploy-ec2-scp.ps1) that handles building the Docker image locally, saving it, copying it to EC2, and running it.

Open PowerShell in the `deployment/` directory and execute:

```powershell
.\deploy-ec2-scp.ps1 -EC2Ip "<YOUR_EC2_PUBLIC_IP>" -KeyPath "C:\path\to\your-key.pem"
```

*Note: Ensure your local Docker Desktop is running before launching this script.*

---

### Strategy 2: Git Build Directly on EC2 (Easiest if Repository is Public/Accessible)

If your git repository is publicly readable or you can pull it using credentials on EC2:

1. **SSH into your EC2 Instance**:
   ```bash
   ssh -i /path/to/your-key.pem ubuntu@<YOUR_EC2_PUBLIC_IP>
   ```
2. **Clone the code**:
   ```bash
   git clone https://github.com/your-username/stock-checker.git
   cd stock-checker
   ```
3. **Build the Docker container**:
   ```bash
   docker build -t stock-checker .
   ```
4. **Run the container**:
   ```bash
   docker run -d --name stock-checker --restart always -p 9091:9091 stock-checker
   ```

---

### Strategy 3: Push to a Registry (ECR / Docker Hub)

For professional or CI/CD setups:
1. Build and push to your container registry:
   ```bash
   docker build -t <your-username>/stock-checker:latest .
   # Log in and push
   docker login
   docker push <your-username>/stock-checker:latest
   ```
2. SSH into your EC2 instance:
   ```bash
   ssh -i /path/to/your-key.pem ubuntu@<YOUR_EC2_PUBLIC_IP>
   ```
3. Pull and run the image:
   ```bash
   docker pull <your-username>/stock-checker:latest
   docker run -d --name stock-checker --restart always -p 9091:9091 <your-username>/stock-checker:latest
   ```

---

## 🔍 Step 3: Verification & Logs

1. Open your browser and navigate to:
   ```
   http://<YOUR_EC2_PUBLIC_IP>:9091
   ```
2. **Monitoring logs on EC2**:
   If you need to debug or watch the execution:
   ```bash
   ssh -i /path/to/your-key.pem ubuntu@<YOUR_EC2_PUBLIC_IP>
   
   # View recent logs
   docker logs stock-checker
   
   # Follow live logs
   docker logs -f stock-checker
   ```
3. **Check container resource utilization**:
   ```bash
   docker stats stock-checker
   ```
