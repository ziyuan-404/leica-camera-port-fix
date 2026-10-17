package local.leica.watermarkfix;

/** Physical panel profiles, independent of Build and spoofed product properties. */
final class RearDevice {
    static final RearDevice PRO = new RearDevice("pandora", "Pandora", 0x7cfe);
    static final RearDevice PRO_MAX = new RearDevice("popsicle", "Popsicle", 0xc578);
    final String name;
    final String featureClass;

    private RearDevice(String name, String feature, int key) {
        this.name = name;
        char[] encoded = ("com.mi.device." + feature).toCharArray();
        for (int i = 0; i < encoded.length; i++) encoded[i] ^= key;
        featureClass = new String(encoded);
    }

    static RearDevice forPanel(int displayId, int displayType, int width, int height) {
        // TYPE_INTERNAL = 1. Exclude the main panel, HDMI, casting and virtual displays.
        if (displayId <= 0 || displayType != 1) return null;
        int longEdge = Math.max(width, height), shortEdge = Math.min(width, height);
        if (longEdge == 904 && shortEdge == 572) return PRO;
        if (longEdge == 976 && shortEdge == 596) return PRO_MAX;
        return null;
    }
}
