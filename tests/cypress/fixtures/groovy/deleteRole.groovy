// Deletes a role created by createServerRole.groovy. Replacement: ROLE_NAME.
import org.jahia.services.content.JCRCallback
import org.jahia.services.content.JCRSessionWrapper
import org.jahia.services.content.JCRTemplate

JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Object>() {
    Object doInJCR(JCRSessionWrapper session) {
        if (session.nodeExists("/roles/ROLE_NAME")) {
            session.getNode("/roles/ROLE_NAME").remove()
            session.save()
        }
        return null
    }
})
