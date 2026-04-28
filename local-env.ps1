$env:SPRING_PROFILES_ACTIVE = "render,mysql"
$env:JWT_SECRET = "replace-with-a-long-random-secret-at-least-32-characters"

# MySQL
# Only needed when SPRING_PROFILES_ACTIVE includes "mysql".
$env:MYSQL_HOST = "mainline.proxy.rlwy.net"
$env:MYSQL_PORT = "45203"
$env:MYSQL_DB = "railway"
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "RiRWtHzdpgbwgKZprvNgsJFxOWvSFSRd"

# Mail
$env:MAIL_ENABLED = "true"
$env:MAIL_HOST = "smtp.gmail.com"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = "noreply.investwise@gmail.com"
$env:MAIL_PASSWORD = "ulmkkbsgjmiqfizv"
$env:MAIL_FROM = "noreply.investwise@gmail.com"
$env:MAIL_SMTP_AUTH = "true"
$env:MAIL_SMTP_STARTTLS = "true"
$env:MAIL_SMTP_STARTTLS_REQUIRED = "true"
$env:MAIL_SMTP_CONNECTION_TIMEOUT = "10000"
$env:MAIL_SMTP_TIMEOUT = "10000"
$env:MAIL_SMTP_WRITE_TIMEOUT = "10000"
