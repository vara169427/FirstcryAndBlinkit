# AWS EC2 Deployment Guide: Stock Checker

This guide provides step-by-step instructions for provisioning your AWS EC2 instance and deploying the **Stock Checker** web application. 

It covers both **Key Pair SSH** access and **Keyless Access** using AWS Systems Manager (SSM) Session Manager.

---

## 🛠️ Prerequisites

Before you begin, ensure you have:
1. An active **AWS Account**.
2. **Docker** installed on your local machine (if using local build/PowerShell script).
3. **SSH Key Pair (Optional)**: If you want to connect via terminal SSH. See [Appendix: Creating a Key Pair](#appendix-creating-a-key-pair) below.

---

## 🚀 Step 1: Provision Infrastructure on AWS

### Option A: Using AWS CloudFormation (Automated & Recommended)

1. Log in to the [AWS Management Console](https://console.aws.amazon.com/).
2. Navigate to **CloudFormation** and click **Create stack** -> **With new resources (standard)**.
3. Choose **Upload a template file** and upload the [aws-infrastructure.yaml](aws-infrastructure.yaml) template.
4. Fill in the parameters:
   - **Stack name**: `stock-checker-stack`
   - **KeyName**: Select your existing EC2 SSH Key Pair, or **leave it blank** to deploy a keyless instance.
   - **InstanceType**: We recommend `t3.small` or `t3.medium` because Playwright runs a resource-intensive Chromium browser.
5. Click **Next**, keep defaults, and click **Submit**.
6. Wait for the stack status to become `CREATE_COMPLETE`. Check the **Outputs** tab to get your **Public IP** and **AppURL**.

---

### Option B: Manual Setup via AWS Console

If you prefer to configure the EC2 instance manually:
1. Go to the **EC2 Dashboard** and click **Launch instance**.
2. Set Name to `Stock-Checker-Host`.
3. Choose **Ubuntu 22.04 LTS** (AMI).
4. Choose **Instance Type**: Select `t3.small` or `t3.medium`.
5. Under **Key pair (login)**:
   - Select an existing Key Pair, or
   - Choose **Proceed without a key pair** (we will connect using SSM instead).
6. Under **Configure Instance Profile** (under *Advanced details*):
   - Select or create an IAM Role containing the policy `AmazonSSMManagedInstanceCore`. This allows keyless connection via AWS Console.
7. Under **Network settings**, create a security group:
   - If using a Key Pair: Allow **SSH** (Port 22) from your IP.
   - Add a custom TCP rule: Allow Port **9091** from Anywhere (`0.0.0.0/0`).
8. Under **Configure storage**, change the size to **20 GiB** (SSD gp3).
9. Under **Advanced details**, expand and paste the following script into the **User data** box to auto-install Docker:
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
10. Click **Launch instance**.

---

## 📦 Step 2: Access & Deploy the Application

Choose the method below depending on whether you are using an SSH Key Pair or connecting keyless.

### Option 1: Keyless Connection (AWS Console Session Manager)

If you deployed the CloudFormation stack without a key pair (or set up manual EC2 with an SSM Instance Profile):

1. Go to the **EC2 Dashboard** in your AWS Console.
2. Select your `Stock-Checker-Host` instance.
3. Click the **Connect** button at the top.
4. Select the **Session Manager** tab and click **Connect**.
5. This opens a Linux terminal in your browser!
6. Switch to the `ubuntu` user and navigate to their home folder:
   ```bash
   sudo su - ubuntu
   cd ~
   ```
7. **Clone the code**:
   ```bash
   git clone https://github.com/your-username/stock-checker.git
   cd stock-checker
   ```
8. **Build and Run the Docker container**:
   ```bash
   docker build -t stock-checker .
   docker run -d --name stock-checker --restart always -p 9091:9091 stock-checker
   ```

---

### Option 2: Using SSH Key Pair

If you have a `.pem` Key Pair file:

#### Strategy A: Using PowerShell Automation Script (Easiest Local Deploy)
We have provided a script [deploy-ec2-scp.ps1](deploy-ec2-scp.ps1) that handles building the Docker image locally, saving it, copying it to EC2, and running it.

Open PowerShell in the `deployment/` directory and execute:
```powershell
.\deploy-ec2-scp.ps1 -EC2Ip "<YOUR_EC2_PUBLIC_IP>" -KeyPath "C:\path\to\your-key.pem"
```
*(Ensure your local Docker Desktop is running before launching this script).*

#### Strategy B: Manual SSH Git Build
1. **SSH into your EC2 Instance**:
   ```bash
   ssh -i /path/to/your-key.pem ubuntu@<YOUR_EC2_PUBLIC_IP>
   ```
2. **Clone, Build, and Run**:
   ```bash
   git clone https://github.com/your-username/stock-checker.git
   cd stock-checker
   docker build -t stock-checker .
   docker run -d --name stock-checker --restart always -p 9091:9091 stock-checker
   ```

---

## 🔍 Step 3: Verification & Logs

1. Open your browser and navigate to:
   ```
   http://<YOUR_EC2_PUBLIC_IP>:9091
   ```
2. **Monitoring logs on EC2** (from either SSH or Session Manager terminal):
   ```bash
   # View recent logs
   docker logs stock-checker
   
   # Follow live logs
   docker logs -f stock-checker
   ```
3. **Check container resource utilization**:
   ```bash
   docker stats stock-checker
   ```

---

## 📄 Appendix: Creating a Key Pair

If you want to use the SSH scripts but don't have a `.pem` key:
1. Open the **EC2 Console**.
2. In the left navigation pane under **Network & Security**, click **Key Pairs**.
3. Click **Create key pair**.
4. Enter a name (e.g. `stock-checker-key`).
5. Choose **RSA** for key type and **.pem** for private key file format.
6. Click **Create key pair** to download the private key file. Save it securely on your local PC (e.g. `C:\Users\YourUser\.ssh\stock-checker-key.pem`).
