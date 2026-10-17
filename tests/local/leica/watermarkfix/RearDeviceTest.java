package local.leica.watermarkfix;

public final class RearDeviceTest {
    public static void main(String[] args) {
        check(RearDevice.forPanel(1, 1, 904, 572) == RearDevice.PRO, "17 Pro panel");
        check(RearDevice.forPanel(7, 1, 596, 976) == RearDevice.PRO_MAX,
                "rotated Pro Max panel with a non-default display id");
        check(RearDevice.forPanel(0, 1, 904, 572) == null, "main display");
        check(RearDevice.forPanel(-1, 1, 976, 596) == null, "invalid display id");
        check(RearDevice.forPanel(1, 2, 904, 572) == null, "HDMI");
        check(RearDevice.forPanel(1, 3, 976, 596) == null, "wireless display");
        check(RearDevice.forPanel(1, 5, 976, 596) == null, "virtual display");
        check(RearDevice.forPanel(1, 1, 1220, 2656) == null, "foldable or second full-size panel");
        check(RearDevice.forPanel(1, 1, 320, 240) == null, "unknown rear panel");
        check(!RearDevice.PRO.featureClass.equals(RearDevice.PRO_MAX.featureClass),
                "each phone must use its own feature class");
        System.out.println("RearDeviceTest: 10 checks passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
