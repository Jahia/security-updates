package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.feed.ForgeSettingsException;
import org.jahia.community.securityupdates.feed.ForgeSettingsReader;
import org.jahia.community.securityupdates.feed.StoreTarget;
import org.jahia.community.securityupdates.feed.StoreUrls;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The stores and feeds a check reads, in priority order:
 * <ol>
 *     <li>the stores of the module manager (when {@code useForgeSettings}), or else the default store;</li>
 *     <li>the extra feeds;</li>
 * </ol>
 * then sorted so that the Jahia Store ({@code store.jahia.com}) comes first, the others keeping that order. When
 * two feeds publish an advisory with the same id, the first one wins, so a private store or an extra feed can
 * never replace an advisory of the Jahia Store. At most {@link #MAX_TARGETS} are read.
 */
final class StoreTargets {

    /** Most stores and feeds read by one check. */
    static final int MAX_TARGETS = 20;
    private static final Logger logger = LoggerFactory.getLogger(StoreTargets.class);

    private StoreTargets() {
    }

    /**
     * @param errors receives the problems met (stores of the module manager unreadable, too many targets)
     */
    static List<StoreTarget> of(Settings settings, ForgeSettingsReader forgeSettingsReader, List<StoreError> errors) {
        List<StoreTarget> targets = new ArrayList<>();
        if (settings.isUseForgeSettings()) {
            try {
                targets.addAll(forgeSettingsReader.readForges());
            } catch (ForgeSettingsException | RuntimeException e) {
                logger.warn("Could not read the module manager stores, using the default store: {}", e.getMessage());
                logger.debug("Store settings read failure", e);
                errors.add(new StoreError(null, StoreErrorKind.UNAVAILABLE, StoreErrorDetail.FORGE_SETTINGS_UNREADABLE,
                        "The stores of the module manager could not be read: the default store is used"));
            }
        }
        if (targets.isEmpty() && !settings.getDefaultStoreUrl().isEmpty()) {
            targets.add(StoreTarget.store(settings.getDefaultStoreUrl(), null, null));
        }
        for (String feedUrl : settings.getExtraFeedUrls()) {
            targets.add(StoreTarget.feed(feedUrl));
        }
        // stable: the Jahia Store first, the others in configured order
        targets.sort(Comparator.comparing(target -> !StoreUrls.isJahiaStore(target.getUrl())));
        if (targets.size() > MAX_TARGETS) {
            logger.warn("{} stores and feeds are configured: only the first {} are checked", targets.size(), MAX_TARGETS);
            errors.add(new StoreError(null, StoreErrorKind.UNAVAILABLE, "Only the first " + MAX_TARGETS
                    + " stores and feeds are checked"));
            return new ArrayList<>(targets.subList(0, MAX_TARGETS));
        }
        return targets;
    }
}
