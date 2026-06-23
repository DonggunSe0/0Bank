
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
# VPC는 AWS 안에서 우리 팀 리소스들을 담는 독립적인 네트워크 공간입니다.
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
  # 리소스 식별, 비용 추적, 팀 구분에 사용합니다.
  tags = {
    # AWS 콘솔에서 보이는 VPC 이름입니다.
    Name = "team10-vpc"

    # 데브코스 요구사항에 따른 팀 공통 태그입니다.
    Team = "devcos-team10"
  }
}
