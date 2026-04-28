$env:SPRING_PROFILES_ACTIVE = "render,mysql"
$env:JWT_SECRET = "replace-with-a-long-random-secret-at-least-32-characters"

# MySQL
# Only needed when SPRING_PROFILES_ACTIVE includes "mysql".
$env:MYSQL_HOST = "127.0.0.1"
$env:MYSQL_PORT = "3306"
$env:MYSQL_DB = "mutual_fund_db"
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "your_mysql_password"

# Mail
$env:MAIL_ENABLED = "true"
$env:MAIL_HOST = "smtp.gmail.com"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = "your_email@gmail.com"
$env:MAIL_PASSWORD = "your_gmail_app_password"
$env:MAIL_FROM = "your_email@gmail.com"
$env:MAIL_SMTP_AUTH = "true"
$env:MAIL_SMTP_STARTTLS = "true"
$env:MAIL_SMTP_STARTTLS_REQUIRED = "true"
$env:MAIL_SMTP_CONNECTION_TIMEOUT = "10000"
$env:MAIL_SMTP_TIMEOUT = "10000"
$env:MAIL_SMTP_WRITE_TIMEOUT = "10000"
# Gmail works best when MAIL_FROM matches MAIL_USERNAME.

# Optional extras
# $env:GOOGLE_CLIENT_ID = "your-google-client-id.apps.googleusercontent.com"
# $env:MAIL_TEST_CONNECTION = "true"
