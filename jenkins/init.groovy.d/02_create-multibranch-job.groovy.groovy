import jenkins.model.*
import org.jenkinsci.plugins.github_branch_source.*
import jenkins.branch.*
import org.jenkinsci.plugins.workflow.multibranch.*
import jenkins.plugins.git.traits.CleanBeforeCheckoutTrait

def env = System.getenv()
def instance = Jenkins.get()

def githubRepo = env['GITHUB_REPOSITORY']
def credentialsId = "github-creds"

def mapJobNameToScriptPath = [
        BankHelmApp: "jenkins/Jenkinsfile",
        "accounts-service": "accounts-service/Jenkinsfile",
        "cash-service": "cash-service/Jenkinsfile",
        "transfer-service": "transfer-service/Jenkinsfile",
        "blocker-service": "blocker-service/Jenkinsfile",
        "exchange-service": "exchange-service/Jenkinsfile",
        "exchange-generator": "exchange-generator/Jenkinsfile",
        "notifications-service": "notifications-service/Jenkinsfile",
        "front-ui": "front-ui/Jenkinsfile"
]

println "--> Запуск создания multibranch jobs"

if (!githubRepo) {
    println "Переменная окружения GITHUB_REPOSITORY не задана (пример: owner/repo)"
    return
}

// Разбиваем owner/repo
def parts = githubRepo.split('/')
if (parts.length != 2) {
    println "Неверный формат GITHUB_REPOSITORY. Ожидалось: owner/repo"
    return
}
def owner = parts[0]
def repo  = parts[1]

mapJobNameToScriptPath.forEach { jobName, scriptPath ->
    println "--> Проверяем job: ${jobName}"

    def job = instance.getItem(jobName)
    if (job != null) {
        println "    Job '${jobName}' уже существует. Пропускаем."
        return // переход к следующей итерации
    }

    println "    Создаём job '${jobName}' с Jenkinsfile: ${scriptPath}"

// Создаём GitHub SCM Source
    def source = new GitHubSCMSource(owner, repo)
    source.setCredentialsId(credentialsId)
    source.setTraits([
            new BranchDiscoveryTrait(1),
            new OriginPullRequestDiscoveryTrait(1),
            new ForkPullRequestDiscoveryTrait(1, new ForkPullRequestDiscoveryTrait.TrustPermission()),
            new CleanBeforeCheckoutTrait()
    ])

    def branchSource = new BranchSource(source)
    branchSource.setStrategy(new DefaultBranchPropertyStrategy([] as BranchProperty[]))

    def mbp = new WorkflowMultiBranchProject(instance, jobName)
    mbp.getSourcesList().add(branchSource)

    def factory = new WorkflowBranchProjectFactory()
    factory.setScriptPath(scriptPath)
    mbp.setProjectFactory(factory)

    instance.add(mbp, jobName)
    mbp.save()
    mbp.scheduleBuild2(0)

    println "    Job '${jobName}' успешно создана и запущена."
}

println "--> Готово."