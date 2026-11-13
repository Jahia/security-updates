package org.jahia.community.securityupdates.feed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JcrForgeSettingsReader")
class JcrForgeSettingsReaderTest {

    @Test
    @DisplayName("no forge settings node means no forge")
    void noSettings() throws RepositoryException {
        Session session = mock(Session.class);
        when(session.itemExists(JcrForgeSettingsReader.FORGES_PATH)).thenReturn(false);

        assertThat(JcrForgeSettingsReader.read(session)).isEmpty();
    }

    @Test
    @DisplayName("reads url, user and password of every jnt:forgeServerSettings node")
    void readsForges() throws RepositoryException {
        Node withCredentials = forge("https://store.example.org", "jdoe", "secret");
        Node anonymous = forge("https://store.jahia.com", null, null);
        Node other = mock(Node.class);
        when(other.isNodeType(JcrForgeSettingsReader.FORGE_TYPE)).thenReturn(false);
        Node noUrl = forge(null, "x", "y");
        Session session = sessionWith(withCredentials, other, anonymous, noUrl);

        List<StoreTarget> targets = JcrForgeSettingsReader.read(session);

        assertThat(targets).extracting(StoreTarget::getUrl).containsExactly("https://store.example.org", "https://store.jahia.com");
        assertThat(targets.get(0).getUser()).isEqualTo("jdoe");
        assertThat(targets.get(0).getPassword()).isEqualTo("secret");
        assertThat(targets.get(1).hasCredentials()).isFalse();
    }

    @Test
    @DisplayName("a repository failure is passed to the caller")
    void repositoryFailure() throws RepositoryException {
        Session session = org.mockito.Mockito.mock(Session.class);
        org.mockito.Mockito.when(session.itemExists(JcrForgeSettingsReader.FORGES_PATH)).thenThrow(new RepositoryException("down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> JcrForgeSettingsReader.read(session))
                .isInstanceOf(RepositoryException.class);
    }

    private static Session sessionWith(Node... forges) throws RepositoryException {
        Session session = mock(Session.class);
        Node root = mock(Node.class);
        when(session.itemExists(JcrForgeSettingsReader.FORGES_PATH)).thenReturn(true);
        when(session.getNode(JcrForgeSettingsReader.FORGES_PATH)).thenReturn(root);
        NodeIterator nodes = iterator(forges);
        when(root.getNodes()).thenReturn(nodes);
        return session;
    }

    private static Node forge(String url, String user, String password) throws RepositoryException {
        Node node = mock(Node.class);
        when(node.isNodeType(JcrForgeSettingsReader.FORGE_TYPE)).thenReturn(true);
        property(node, "j:url", url);
        property(node, "j:user", user);
        property(node, "j:password", password);
        return node;
    }

    private static void property(Node node, String name, String value) throws RepositoryException {
        when(node.hasProperty(name)).thenReturn(value != null);
        if (value != null) {
            Property property = mock(Property.class);
            when(property.getString()).thenReturn(value);
            when(node.getProperty(name)).thenReturn(property);
        }
    }

    private static NodeIterator iterator(Node... nodes) {
        Iterator<Node> it = Arrays.asList(nodes).iterator();
        NodeIterator nodeIterator = mock(NodeIterator.class);
        when(nodeIterator.hasNext()).thenAnswer(i -> it.hasNext());
        when(nodeIterator.nextNode()).thenAnswer(i -> it.next());
        when(nodeIterator.getSize()).thenReturn((long) nodes.length);
        return nodeIterator;
    }
}
