package org.jahia.community.securityupdates.feed;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the stores configured in the module manager: the {@code jnt:forgeServerSettings} nodes under
 * {@code /settings/forgesSettings}, with their {@code j:url}, {@code j:user} and {@code j:password}
 * (as Jahia's module manager {@code ForgeService} does), using a system session. The password is only
 * handed to the store client and never logged.
 */
@Component(service = ForgeSettingsReader.class)
public class JcrForgeSettingsReader implements ForgeSettingsReader {

    static final String FORGES_PATH = "/settings/forgesSettings";
    static final String FORGE_TYPE = "jnt:forgeServerSettings";

    @Override
    public List<StoreTarget> readForges() throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSession(
                (JCRCallback<List<StoreTarget>>) JcrForgeSettingsReader::read);
    }

    static List<StoreTarget> read(Session session) throws RepositoryException {
        List<StoreTarget> targets = new ArrayList<>();
        if (!session.itemExists(FORGES_PATH)) {
            return targets;
        }
        NodeIterator nodes = session.getNode(FORGES_PATH).getNodes();
        while (nodes.hasNext()) {
            Node node = nodes.nextNode();
            String url = node.isNodeType(FORGE_TYPE) ? property(node, "j:url") : null;
            if (url != null) {
                targets.add(StoreTarget.store(url, property(node, "j:user"), property(node, "j:password")));
            }
        }
        return targets;
    }

    private static String property(Node node, String name) throws RepositoryException {
        return node.hasProperty(name) ? node.getProperty(name).getString() : null;
    }
}
