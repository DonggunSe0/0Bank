
# Terraform 자체 설정 블록입니다.
# 여기서는 어떤 Provider(클라우드/서비스 연동 플러그인)를 사용할지 정의합니다.
terraform {
  # 이 Terraform 코드에서 필요한 Provider 목록을 선언합니다.
  required_providers {
    # AWS 리소스를 생성/관리하기 위해 AWS Provider를 사용합니다.
    aws = {
      # HashiCorp에서 제공하는 공식 AWS Provider를 사용합니다.
      source = "hashicorp/aws"

      # AWS Provider의 메이저 버전 5.x를 사용하도록 고정합니다.
      # 예: 5.0 이상, 6.0 미만 버전 사용
      version = "~> 5.0"
    }
  }

}

# AWS Provider 설정입니다.
# Terraform이 AWS API를 호출할 때 사용할 리전 정보를 지정합니다.
provider "aws" {
  region  = var.region
  profile = var.profile
}

# VPC를 생성합니다.
resource "aws_vpc" "main" {
  # VPC에서 사용할 사설 IP 대역입니다.
  # 10.0.0.0/16은 10.0.x.x 범위의 IP를 이 VPC 안에서 사용할 수 있다는 의미입니다.
  cidr_block = "10.0.0.0/16"

  # VPC 내부 리소스가 DNS 해석을 사용할 수 있도록 활성화합니다.
  # 예를 들어 AWS 내부 도메인 이름을 IP로 변환할 때 필요합니다.
  enable_dns_support = true

  # VPC 내부 리소스에 DNS 호스트 이름을 부여할 수 있도록 활성화합니다.
  # EC2 Public DNS나 Private DNS 사용에 필요합니다.
  enable_dns_hostnames = true

  # AWS 리소스에 붙이는 태그입니다.
  tags = {
    Name = "team10-vpc" # AWS 콘솔에서 보이는 VPC 이름입니다.
    Team = var.team     # 팀 공통 태그입니다.
  }
}

# public subnet 설정
resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${var.region}a"
  map_public_ip_on_launch = true

  tags = {
    Name = "team10-public-subnet"
    Team = var.team
  }
}

# private subnet 설정
resource "aws_subnet" "private" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = "${var.region}b"
  map_public_ip_on_launch = false

  tags = {
    Name = "team10-private-subnet"
    Team = var.team
  }
}

# Internet Gateway를 생성합니다.
# Internet Gateway는 VPC가 외부 인터넷과 통신할 수 있도록 연결해주는 출입문 역할을 합니다.
# Public Subnet에 위치한 EC2가 인터넷에서 요청을 받거나, 인터넷으로 나갈 때 필요합니다.
resource "aws_internet_gateway" "main" {
  # Internet Gateway를 연결할 VPC ID입니다.
  # 위에서 생성한 team10-vpc에 Internet Gateway를 연결합니다.
  vpc_id = aws_vpc.main.id

  # AWS 리소스에 붙이는 태그입니다.
  tags = {
    Name = "team10-igw" # AWS 콘솔에서 보이는 Internet Gateway 이름입니다.
    Team = var.team     # 팀 공통 태그입니다.
  }
}

# Public Route Table을 생성합니다.
# Route Table은 Subnet의 네트워크 트래픽을 어디로 보낼지 결정하는 라우팅 규칙 모음입니다.
# 이 Route Table은 Public Subnet에 연결해서 인터넷 통신이 가능하도록 사용할 예정입니다.
resource "aws_route_table" "public" {
  # 이 Route Table이 소속될 VPC ID입니다.
  vpc_id = aws_vpc.main.id

  # 외부 인터넷으로 나가는 모든 트래픽에 대한 라우팅 규칙입니다.
  route {
    # 0.0.0.0/0은 VPC 내부 대역을 제외한 모든 IPv4 목적지를 의미합니다.
    # 쉽게 말해, 인터넷으로 나가는 모든 요청을 의미합니다.
    cidr_block = "0.0.0.0/0"

    # 위 트래픽을 Internet Gateway로 보내도록 설정합니다.
    gateway_id = aws_internet_gateway.main.id
  }

  # AWS 리소스에 붙이는 태그입니다.
  tags = {
    Name = "team10-public-rt" # AWS 콘솔에서 보이는 Public Route Table 이름입니다.
    Team = var.team           # 팀 공통 태그입니다.
  }
}

# Public Subnet과 Public Route Table을 연결합니다.
# 이 연결이 있어야 Public Subnet에 있는 EC2가 0.0.0.0/0 -> IGW 라우팅 규칙을 사용할 수 있습니다.
resource "aws_route_table_association" "public" {
  # Public Route Table을 적용할 Subnet ID입니다.
  subnet_id = aws_subnet.public.id

  # Public Subnet에 연결할 Route Table ID입니다.
  route_table_id = aws_route_table.public.id
}

# Edge 서버용 Security Group을 생성합니다.
# SSH 접속은 열지 않고, 추후 EC2에 SSM IAM Role을 붙여 Session Manager로 접속합니다.
resource "aws_security_group" "edge" {
  name        = "team10-edge-sg"
  description = "Security group for edge server"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Nginx Proxy Manager Admin"
    from_port   = 81
    to_port     = 81
    protocol    = "tcp"
    cidr_blocks = [var.admin_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "team10-edge-sg"
    Team = var.team
  }
}

# Public Subnet에 배치하되 외부에서 직접 접근하지 못하도록 Security Group으로 제한합니다.
# SSH 접속은 열지 않고, 추후 EC2에 SSM IAM Role을 붙여 Session Manager로 접속합니다.
resource "aws_security_group" "app_data" {
  name        = "team10-app-data-sg"
  description = "Security group for app-data server"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Nginx LB"
    from_port       = 8090
    to_port         = 8090
    protocol        = "tcp"
    security_groups = [aws_security_group.edge.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "team10-app-data-sg"
    Team = var.team
  }
}

# EC2 인스턴스에 연결할 IAM Role을 생성합니다.
# 이 Role은 EC2가 AWS Systems Manager(SSM)와 통신할 수 있도록 권한을 부여하는 용도입니다.
# SSH 포트(22)를 열지 않고 Session Manager로 EC2에 접속하기 위해 필요합니다.
resource "aws_iam_role" "ec2_ssm_role" {
  name = "team10-ec2-ssm-role"

  # 신뢰 정책입니다.
  # 이 Role을 어떤 AWS 서비스가 사용할 수 있는지 정의합니다.
  # 여기서는 EC2 서비스가 이 Role을 AssumeRole 할 수 있도록 허용합니다.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  # AWS 리소스에 붙이는 태그입니다.
  tags = {
    Name = "team10-ec2-ssm-role" # AWS 콘솔에서 보이는 IAM Role 이름입니다.
    Team = var.team              # 팀 공통 태그입니다.
  }
}

# EC2 IAM Role에 SSM 접속에 필요한 관리형 정책을 연결합니다.
# AmazonSSMManagedInstanceCore 정책은 EC2가 Systems Manager에 등록되고,
# Session Manager를 통해 접속될 수 있도록 필요한 최소 권한을 제공합니다.
resource "aws_iam_role_policy_attachment" "ec2_ssm_core" {
  # 위에서 생성한 EC2 IAM Role 이름입니다.
  role = aws_iam_role.ec2_ssm_role.name

  # AWS에서 제공하는 SSM 관리형 정책 ARN입니다.
  # 예제 코드의 AmazonEC2RoleforSSM보다 현재는 이 정책 사용이 권장됩니다.
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# IAM Instance Profile을 생성합니다.
# EC2는 IAM Role을 직접 붙이는 것이 아니라 Instance Profile을 통해 Role을 연결합니다.
# 이후 aws_instance 리소스에서 iam_instance_profile 값으로 이 Profile을 지정합니다.
resource "aws_iam_instance_profile" "ec2_ssm_profile" {
  # Instance Profile 이름입니다.
  name = "team10-ec2-ssm-profile"

  # Instance Profile에 연결할 IAM Role입니다.
  role = aws_iam_role.ec2_ssm_role.name

  # AWS 리소스에 붙이는 태그입니다.
  tags = {
    Name = "team10-ec2-ssm-profile" # AWS 콘솔에서 보이는 Instance Profile 이름입니다.
    Team = var.team                 # 팀 공통 태그입니다.
  }
}

# 최신 Amazon Linux 2023 AMI를 조회합니다.
# EC2를 생성할 때는 운영체제 이미지인 AMI ID가 필요합니다.
# AMI ID는 리전과 시점에 따라 달라질 수 있으므로, 하드코딩하지 않고 조건에 맞는 최신 AMI를 자동 조회합니다.
data "aws_ami" "latest_amazon_linux" {
  # 조건에 맞는 AMI가 여러 개 있을 때 가장 최신 이미지를 선택합니다.
  most_recent = true

  # Amazon에서 공식 제공하는 AMI만 조회합니다.
  owners = ["amazon"]

  # Amazon Linux 2023 AMI 이름 패턴을 기준으로 필터링합니다.
  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  # x86_64 아키텍처 이미지만 조회합니다.
  # t3 계열 인스턴스는 x86_64 AMI를 사용할 수 있습니다.
  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  # HVM 가상화 타입 이미지만 조회합니다.
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  # EBS 기반 루트 디바이스를 사용하는 AMI만 조회합니다.
  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }
}

# Edge EC2 인스턴스를 생성합니다.
# 이 서버는 외부 요청을 가장 먼저 받는 진입점 역할을 합니다.
# Nginx Proxy Manager, HTTPS 인증서 처리, Prometheus/Grafana 등을 배치할 예정입니다.
resource "aws_instance" "edge" {
  # 위에서 조회한 최신 Amazon Linux 2023 AMI ID를 사용합니다.
  ami = data.aws_ami.latest_amazon_linux.id

  # Edge 서버 인스턴스 타입입니다.
  # Nginx Proxy Manager와 모니터링 도구를 고려하여 t3.small을 사용합니다.
  instance_type = "t3.small"

  # Edge 서버는 외부 요청을 받아야 하므로 Public Subnet에 배치합니다.
  subnet_id = aws_subnet.public.id

  # Edge 서버용 Security Group을 연결합니다.
  # 80/443은 전체 허용, 81은 관리자 IP만 허용합니다.
  vpc_security_group_ids = [aws_security_group.edge.id]

  # Public Subnet에 배치된 EC2에 Public IP를 자동 할당합니다.
  # 추후 Elastic IP를 연결하면 고정 IP로 교체됩니다.
  associate_public_ip_address = true

  # SSM Session Manager 접속을 위해 EC2에 Instance Profile을 연결합니다.
  # SSH 포트를 열지 않고도 AWS 콘솔에서 EC2에 접속할 수 있게 됩니다.
  iam_instance_profile = aws_iam_instance_profile.ec2_ssm_profile.name

  # EC2 최초 생성 시 Docker 및 NPM을 자동 설치합니다.
  user_data = templatefile("${path.module}/user-data/edge.sh.tftpl",
    {
      compose_yml = file("${path.module}/compose/edge.docker-compose.yml")
    }
  )

  user_data_replace_on_change = false

  # 루트 EBS 볼륨 설정입니다.
  # Docker 이미지, Nginx Proxy Manager 데이터, 모니터링 데이터 등을 고려하여 30GiB로 설정합니다.
  root_block_device {
    volume_type = "gp3"
    volume_size = 30
  }

  # AWS 리소스에 붙이는 태그입니다.
  tags = {
    Name = "team10-edge-ec2" # AWS 콘솔에서 보이는 Edge EC2 이름입니다.
    Team = var.team          # 팀 공통 태그입니다.
  }
}

# App-Data EC2 인스턴스를 생성합니다.
# 이 서버는 Spring App 2개, MySQL, Redis를 함께 실행하는 역할입니다.
# 비용과 운영 난이도를 고려하여 App 계층과 Data 계층을 하나의 t3.medium 인스턴스에 통합합니다.
resource "aws_instance" "app_data" {
  # 위에서 조회한 최신 Amazon Linux 2023 AMI ID를 사용합니다.
  ami = data.aws_ami.latest_amazon_linux.id

  # App-Data 서버 인스턴스 타입입니다.
  # Spring JVM 2개와 MySQL, Redis를 함께 실행해야 하므로 t3.medium을 사용합니다.
  instance_type = "t3.medium"

  # NAT Gateway를 사용하지 않는 교육용 환경이므로 App-Data 서버도 Public Subnet에 배치합니다.
  # 단, Security Group을 통해 외부 직접 접근은 차단합니다.
  subnet_id = aws_subnet.public.id

  # App-Data 서버용 Security Group을 연결합니다.
  # Nginx LB 포트 8090은 Edge Security Group에서만 접근할 수 있습니다.
  vpc_security_group_ids = [aws_security_group.app_data.id]

  # Public Subnet에 배치된 EC2에 Public IP를 자동 할당합니다.
  # Docker 이미지 Pull, 패키지 설치, 외부 API 호출 등을 위해 필요합니다.
  associate_public_ip_address = true

  # SSM Session Manager 접속을 위해 EC2에 Instance Profile을 연결합니다.
  # SSH 포트를 열지 않고도 AWS 콘솔에서 EC2에 접속할 수 있게 됩니다.
  iam_instance_profile = aws_iam_instance_profile.ec2_ssm_profile.name

  # EC2 최초 생성 시 Docker 및 기본 시스템 설정을 자동 적용합니다.
  user_data = templatefile("${path.module}/user-data/app-data.sh.tftpl",
    {
      compose_yml         = file("${path.module}/compose/app-data.docker-compose.yml")
      mysql_root_password = var.mysql_root_password
      redis_password      = var.redis_password
    }
  )

  user_data_replace_on_change = false

  # 루트 EBS 볼륨 설정입니다.
  # Spring 이미지, MySQL 데이터, Redis 데이터, 로그 저장을 고려하여 40GiB로 설정합니다.
  root_block_device {
    volume_type = "gp3"
    volume_size = 40
  }

  # AWS 리소스에 붙이는 태그입니다.
  tags = {
    Name = "team10-app-data-ec2" # AWS 콘솔에서 보이는 App-Data EC2 이름입니다.
    Team = var.team              # 팀 공통 태그입니다.
  }
}

# Edge EC2에 연결할 Elastic IP를 생성합니다.
resource "aws_eip" "edge" {
  # VPC 내부 EC2에 연결할 Elastic IP임을 의미합니다.
  domain = "vpc"

  # AWS 리소스에 붙이는 태그입니다.
  tags = {
    Name = "team10-edge-eip" # AWS 콘솔에서 보이는 Elastic IP 이름입니다.
    Team = var.team          # 팀 공통 태그입니다.
  }
}

# 생성한 Elastic IP를 Edge EC2 인스턴스에 연결합니다.
# 이후 도메인을 구매하면 api.example.com 같은 백엔드 도메인의 A Record를 이 Elastic IP로 연결합니다.
resource "aws_eip_association" "edge" {
  # Elastic IP를 연결할 Edge EC2 인스턴스 ID입니다.
  instance_id = aws_instance.edge.id

  # 위에서 생성한 Elastic IP의 Allocation ID입니다.
  allocation_id = aws_eip.edge.id
}
