// Deletes the time of the last security email (email-state.properties in the data area of the module bundle), so
// that the next due email is sent. Run while the module is stopped: a started module keeps the time in memory.
import org.jahia.osgi.BundleUtils

def bundle = BundleUtils.getBundleBySymbolicName("security-updates", null)
def file = bundle == null ? null : bundle.getDataFile("email-state.properties")
if (file != null && file.exists() && !file.delete()) {
    throw new IllegalStateException("Could not delete " + file)
}
log.info("security-updates: email state reset (file {})", file)
