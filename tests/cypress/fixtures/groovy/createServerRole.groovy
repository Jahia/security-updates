// Creates (or replaces) a server role on the root node, like the built-in roles of root-roles.xml.
// Replacements: ROLE_NAME (a node name), PERMISSION_NAMES (permission node names separated by commas).
import org.jahia.services.content.JCRCallback
import org.jahia.services.content.JCRSessionWrapper
import org.jahia.services.content.JCRTemplate

JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Object>() {
    Object doInJCR(JCRSessionWrapper session) {
        def roles = session.getNode("/roles")
        if (roles.hasNode("ROLE_NAME")) {
            roles.getNode("ROLE_NAME").remove()
        }
        def role = roles.addNode("ROLE_NAME", "jnt:role")
        role.setProperty("j:roleGroup", "server-role")
        role.setProperty("j:nodeTypes", ["rep:root"] as String[])
        role.setProperty("j:privilegedAccess", true)
        role.setProperty("j:hidden", false)
        role.setProperty("j:permissionNames", "PERMISSION_NAMES".split(",")*.trim() as String[])
        session.save()
        return null
    }
})
