# 문항 은행(itembank) dev 환경 DB — 실습용 더미 코드. 실제 AWS 계정에 적용하지 않는다.

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.67"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "itembank"
      Env       = "dev"
      Owner     = "platform-team"
      ManagedBy = "terraform"
    }
  }
}

# 문항 은행 API 서버가 붙는 DB 의 보안 그룹
resource "aws_security_group" "itembank_db" {
  name        = "itembank-dev-db-sg"
  description = "itembank dev DB (MariaDB 3306)"
  vpc_id      = var.vpc_id

  # 주의: 3306 을 모든 출발지(0.0.0.0/0)에 열어 둔 상태다. 실습용 더미라서 둔 설정이며 실제 환경에서는 var.api_subnet_cidr 로 제한한다.
  ingress {
    description = "MariaDB from anywhere"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_subnet_group" "itembank" {
  name       = "itembank-dev-db-subnets"
  subnet_ids = var.db_subnet_ids
}

resource "aws_db_instance" "itembank" {
  # 주의: identifier 변경은 인스턴스 교체(삭제 후 재생성)를 일으킨다. 데이터는 새 인스턴스로 옮겨지지 않는다.
  identifier     = "itembank-db-v2"
  engine         = "mariadb"
  engine_version = "10.11"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "itembank"
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.itembank.name
  vpc_security_group_ids = [aws_security_group.itembank_db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period   = 7
  skip_final_snapshot       = false
  final_snapshot_identifier = "itembank-dev-db-final"
}
