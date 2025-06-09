import groovy.json.JsonSlurper

def request = new JsonSlurper().parseText(args);
assert request.adminPassword: 'adminPassword parameter is required';
assert request.jenkinsPassword: 'jenkinsPassword parameter is required';


security.setAnonymousAccess(false)
log.info('Anonymous access disabled')
//
// Create new admin user
//
def adminRole = ['nx-admin']
def janeDoe = security.addUser('ci_admin', 'CI', 'Administrator', 'ci_admin@example.com', true, request.adminPassword, adminRole)
log.info('User ci_admin created')

//
// Create a new role that allows a user same access as anonymous and adds healtchcheck access
//
def devPrivileges = ['nx-healthcheck-read', 'nx-healthcheck-summary-read']
def anoRole = ['nx-anonymous']
// add roles that uses the built in nx-anonymous role as a basis and adds more privileges
security.addRole('developer', 'Developer', 'User with privileges to allow read access to repo content and healtcheck', devPrivileges, anoRole)
log.info('Role developer created')
//
// Create new role that allows deployment and create a user to be used on a CI server
//
// privileges with pattern * to allow any format, browse and read are already part of nx-anonymous
def depPrivileges = ['nx-repository-view-*-*-add', 'nx-repository-view-*-*-edit']
def roles = ['developer']
// add roles that uses the developer role as a basis and adds more privileges
security.addRole('deployer', 'Deployer', 'User with privileges to allow deployment all repositories', depPrivileges, roles)
log.info('Role deployer created')
def depRoles = ['deployer']
def lJenkins = security.addUser('jenkins', 'Leeroy', 'Jenkins', 'leeroy.jenkins@example.com', true, request.jenkinsPassword, depRoles)
log.info('User jenkins created')


log.info('Script security completed successfully')