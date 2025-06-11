import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.impl.*
import hudson.util.Secret
import jenkins.model.*
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl

// Получаем переменные окружения
def env = System.getenv()

def githubUsername = env['GITHUB_USERNAME']
def githubToken = env['GITHUB_TOKEN']
def ghcrToken = env['GHCR_TOKEN']
def dockerRegistry = env['DOCKER_REGISTRY']
def dbAccountsPassword = env['ACCOUNTS_SERVICE_DB_PASSWORD']
def dbCashPassword = env['CASH_SERVICE_DB_PASSWORD']
def dbTransferPassword = env['TRANSFER_SERVICE_DB_PASSWORD']
def dbExchangePassword = env['EXCHANGE_SERVICE_DB_PASSWORD']
def dbNotificationsPassword = env['NOTIFICATIONS_SERVICE_DB_PASSWORD']

def rabbitmqPassword = env['RABBITMQ_DEFAULT_PASS']
def keycloakPassword = env['KEYCLOAK_PASS']

def accountsClientSecret = env['ACCOUNTS_CLIENT_SECRET']
def cashClientSecret = env['CASH_CLIENT_SECRET']
def exchangeClientSecret = env['EXCHANGE_CLIENT_SECRET']
def notificationsClientSecret = env['NOTIFICATION_CLIENT_SECRET']
def transferClientSecret = env['TRANSFER_CLIENT_SECRET']
def blockerClientSecret = env['BLOCKER_CLIENT_SECRET']

def nexusPassword = env['NEXUS_JENKINS_PASSWORD']

// Получаем хранилище учётных данных
def store = Jenkins.instance.getExtensionList(
        'com.cloudbees.plugins.credentials.SystemCredentialsProvider'
)[0].getStore()

// Username + Password (GitHub)
if (githubUsername && githubToken) {
    println "--> Creating credential: github-creds (username + token)"
    def githubCreds = new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL,
            "github-creds",
            "GitHub credentials from ENV",
            githubUsername,
            githubToken
    )
    store.addCredentials(Domain.global(), githubCreds)
}

// Создаём отдельный строковый креденшл с GitHub username (для docker login)
if (githubUsername) {
    println "--> Creating credential: GITHUB_USERNAME (plain string)"
    def usernameCred = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "GITHUB_USERNAME",
            "GitHub username only (for GHCR login)",
            Secret.fromString(githubUsername)
    )
    store.addCredentials(Domain.global(), usernameCred)
}

// Создаём токен доступа к GHCR (GitHub Container Registry)
if (ghcrToken) {
    println "--> Creating credential: GHCR_TOKEN"
    def ghcrCred = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "GHCR_TOKEN",
            "GHCR token from ENV",
            Secret.fromString(ghcrToken)
    )
    store.addCredentials(Domain.global(), ghcrCred)
}

// Создаём строковый креденшл с адресом docker-реестра (например, ghcr.io/username)
if (dockerRegistry) {
    println "--> Creating credential: DOCKER_REGISTRY"
    def registryCred = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "DOCKER_REGISTRY",
            "Docker registry address from ENV",
            Secret.fromString(dockerRegistry)
    )
    store.addCredentials(Domain.global(), registryCred)
}

// Создаём пароль базы accounts-db данных (используется в helm и kubectl)
if (dbAccountsPassword) {
    println "--> Creating credential: ACCOUNTS_SERVICE_DB_PASSWORD"
    def accountsDbCred = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "ACCOUNTS_SERVICE_DB_PASSWORD",
            "Database password from ENV",
            Secret.fromString(dbAccountsPassword)
    )
    store.addCredentials(Domain.global(), accountsDbCred)
}
// Создаём пароль базы cash-db данных (используется в helm и kubectl)
if (dbCashPassword) {
    println "--> Creating credential: CASH_SERVICE_DB_PASSWORD"
    def cashDbCred = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "CASH_SERVICE_DB_PASSWORD",
            "Database password from ENV",
            Secret.fromString(dbCashPassword)
    )
    store.addCredentials(Domain.global(), cashDbCred)
}
// Создаём пароль базы transfer-db данных (используется в helm и kubectl)
if (dbTransferPassword) {
    println "--> Creating credential: TRANSFER_SERVICE_DB_PASSWORD"
    def transferDbCred = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "TRANSFER_SERVICE_DB_PASSWORD",
            "Database password from ENV",
            Secret.fromString(dbTransferPassword)
    )
    store.addCredentials(Domain.global(), transferDbCred)
}
// Создаём пароль базы exchange-db данных (используется в helm и kubectl)
if (dbExchangePassword) {
    println "--> Creating credential: EXCHANGE_SERVICE_DB_PASSWORD"
    def exchangeDbCred = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "EXCHANGE_SERVICE_DB_PASSWORD",
            "Database password from ENV",
            Secret.fromString(dbExchangePassword)
    )
    store.addCredentials(Domain.global(), exchangeDbCred)
}
// Создаём пароль базы notifications-db данных (используется в helm и kubectl)
if (dbNotificationsPassword) {
    println "--> Creating credential: NOTIFICATIONS_SERVICE_DB_PASSWORD"
    def notificationsDbCred = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "NOTIFICATIONS_SERVICE_DB_PASSWORD",
            "Database password from ENV",
            Secret.fromString(dbNotificationsPassword)
    )
    store.addCredentials(Domain.global(), notificationsDbCred)
}

// Создаём секрет клиента keycloak accounts-client (используется в helm и kubectl)
if (accountsClientSecret) {
    println "--> Creating credential: ACCOUNTS_CLIENT_SECRET"
    def accountsCltSrt = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "ACCOUNTS_CLIENT_SECRET",
            "Client secret from ENV",
            Secret.fromString(accountsClientSecret)
    )
    store.addCredentials(Domain.global(), accountsCltSrt)
}
// Создаём секрет клиента keycloak cash-client (используется в helm и kubectl)
if (cashClientSecret) {
    println "--> Creating credential: CASH_CLIENT_SECRET"
    def cashCltSrt = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "CASH_CLIENT_SECRET",
            "Client secret from ENV",
            Secret.fromString(cashClientSecret)
    )
    store.addCredentials(Domain.global(), cashCltSrt)
}
// Создаём секрет клиента keycloak exchange-client (используется в helm и kubectl)
if (exchangeClientSecret) {
    println "--> Creating credential: EXCHANGE_CLIENT_SECRET"
    def exchangeCltSrt = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "EXCHANGE_CLIENT_SECRET",
            "Client secret from ENV",
            Secret.fromString(exchangeClientSecret)
    )
    store.addCredentials(Domain.global(), exchangeCltSrt)
}
// Создаём секрет клиента keycloak notifications-client (используется в helm и kubectl)
if (notificationsClientSecret) {
    println "--> Creating credential: NOTIFICATION_CLIENT_SECRET"
    def nitificationsCltSrt = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "NOTIFICATION_CLIENT_SECRET",
            "Client secret from ENV",
            Secret.fromString(notificationsClientSecret)
    )
    store.addCredentials(Domain.global(), nitificationsCltSrt)
}
// Создаём секрет клиента keycloak transfer-client (используется в helm и kubectl)
if (transferClientSecret) {
    println "--> Creating credential: TRANSFER_CLIENT_SECRET"
    def transferCltSrt = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "TRANSFER_CLIENT_SECRET",
            "Client secret from ENV",
            Secret.fromString(transferClientSecret)
    )
    store.addCredentials(Domain.global(), transferCltSrt)
}
// Создаём секрет клиента keycloak blocker-client (используется в helm и kubectl)
if (blockerClientSecret) {
    println "--> Creating credential: BLOCKER_CLIENT_SECRET"
    def blockerCltSrt = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "BLOCKER_CLIENT_SECRET",
            "Client secret from ENV",
            Secret.fromString(blockerClientSecret)
    )
    store.addCredentials(Domain.global(), blockerCltSrt)
}

// Создаём пароль RabbitMQ (используется в helm и kubectl)
if (rabbitmqPassword) {
    println "--> Creating credential: RABBITMQ_DEFAULT_PASS"
    def rabbitPass = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "RABBITMQ_DEFAULT_PASS",
            "RabbitMq password from ENV",
            Secret.fromString(rabbitmqPassword)
    )
    store.addCredentials(Domain.global(), rabbitPass)
}

// Создаём пароль Keycloak (используется в helm и kubectl)
if (keycloakPassword) {
    println "--> Creating credential: KEYCLOAK_PASS"
    def keycloakPass = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "KEYCLOAK_PASS",
            "Keycloak password from ENV",
            Secret.fromString(keycloakPassword)
    )
    store.addCredentials(Domain.global(), keycloakPass)
}

// Создаём пароль для работы с Nexus
if (nexusPassword) {
    println "--> Creating credential: NEXUS_JENKINS_PASSWORD"
    def nexusPass = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            "NEXUS_JENKINS_PASSWORD",
            "Nexus password from ENV",
            Secret.fromString(nexusPassword)
    )
    store.addCredentials(Domain.global(), nexusPass)
}

println "--> Credential setup complete."