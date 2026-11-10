package pl.vd.stand;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zapisany wygląd stojaka (armor stand): przełączniki, wyposażenie, pozy,
 * yaw/pitch, "sink" (wpuszczenie w blok) oraz doczepione części (bloki/
 * przedmioty ze skalą, obrotem i przesunięciem). Kąty trzymane w stopniach -
 * zamiana na radiany następuje dopiero przy nakładaniu na stojak.
 */
public class StandPreset {

    /** Zapisana, doczepiona część (blok albo przedmiot) - pełny stan wizualny. */
    public static class PartData {
        public String material = "STONE";
        public double scaleX = 1, scaleY = 1, scaleZ = 1;
        public double rotX, rotY, rotZ; // stopnie
        public double tx, ty, tz; // przesunięcie względem stojaka
        /** Część obrotowa wieżyczki (górna część) - obraca się wokół osi pionowej stojaka. */
        public boolean turret;
        /** Własna nazwa części (np. z nazwanego przedmiotu) - pokazywana w action barze. */
        public String name;
        /** Pełna jasność części (ŚWIATŁO w SUGAR) - część wygląda jak rozświetlona. */
        public boolean bright;

        /** Kompaktowy zapis do PDC przedmiotu (10 pól + flaga turret + nazwa + jasność, "|"). */
        public String serialize() {
            return material + "|" + scaleX + "|" + scaleY + "|" + scaleZ + "|"
                    + rotX + "|" + rotY + "|" + rotZ + "|" + tx + "|" + ty + "|" + tz
                    + "|" + (turret ? 1 : 0) + "|" + (name == null ? "" : name)
                    + "|" + (bright ? 1 : 0);
        }

        public static PartData deserialize(String raw) {
            String[] f = raw.split("\\|");
            // stare zapisy: 10 pól (bez turret) albo 11 (z turret); nowe: 12 (turret + nazwa),
            // najnowsze: 13 (+ jasność)
            if (f.length != 10 && f.length != 11 && f.length != 12 && f.length != 13) return null;
            try {
                PartData d = new PartData();
                d.material = f[0].toUpperCase(Locale.ROOT);
                d.scaleX = Double.parseDouble(f[1]);
                d.scaleY = Double.parseDouble(f[2]);
                d.scaleZ = Double.parseDouble(f[3]);
                d.rotX = Double.parseDouble(f[4]);
                d.rotY = Double.parseDouble(f[5]);
                d.rotZ = Double.parseDouble(f[6]);
                d.tx = Double.parseDouble(f[7]);
                d.ty = Double.parseDouble(f[8]);
                d.tz = Double.parseDouble(f[9]);
                d.turret = f.length >= 11 && f[10].equals("1");
                if (f.length >= 12 && !f[11].isEmpty()) d.name = f[11];
                d.bright = f.length >= 13 && f[12].equals("1");
                return d;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * Migracja sinka: pliki zapisane przed poprawką (stary domyślny sink 1.0)
     * stawały 2 bloki pod wierzchem bloku. Taka wartość była prawie na pewno
     * domyślnym, niecelowym sinkiem - korygujemy ją na -1.0 (stojak NA wierzchu
     * bloku). Inne wartości (0.5 = pół bloku niżej itd.) zostają bez zmian.
     */
    static double normalizeSink(double sink) {
        return sink == 1.0 ? -1.0 : sink;
    }

    public String name = "stojak";
    public boolean small;
    public boolean arms = true;
    public boolean visible;
    public boolean base;
    public String head = "AIR";
    public String hand = "AIR";
    public String offhand = "AIR";
    public double headX, headY, headZ;
    public double bodyX, bodyY, bodyZ;
    public double rightX, rightY, rightZ;
    public double leftX, leftY, leftZ;
    public double rightLegX, rightLegY, rightLegZ;
    public double leftLegX, leftLegY, leftLegZ;
    /** Tag encji stojaka-krzesła (zapisuje się z encją i w pliku presetu). */
    public static final String SEAT_TAG = "vd_siedzenie";

    public double yaw;
    public double pitch;
    /** Domyślnie stojak staje NA WIERZCHU patrzonego bloku (sink = -1.0);
     *  większe wartości = głębiej w blok (0 = 1 blok niżej, 1 = 2 bloki niżej). */
    public double sink = -1.0;
    /** Czy to krzesło (/stojak funkcja siedzenie) - kliknięcie = siadanie. */
    public boolean seat;
    /** Wymagana nazwa krzesła (/stojak krzeslo <nazwa> on) - siadać można tylko
     *  na stojaku z widoczną nazwą równą tej (null = bez ograniczenia). */
    public String seatName;
    /** Maksymalne życie wieżyczki (0 = bez życia / niezniszczalna) - w punktach HP (1 serce = 2 HP). */
    public double hp;
    /** DEKORACJA mapy: stojak NIE jest usuwany po zakończeniu areny i nie działa
     *  jak wieżyczka (bez strzałów, bariery i paska HP). Wpis w presecie: dekoracja: true. */
    public boolean dekoracja;
    /** Klik-przełącznik stanów (/stojak funkcja zmiana <A> <B>): nazwy dwóch
     *  zapisanych presetów, między którymi kliknięcie podmienia części stojaka.
     *  null = stojak nie jest przełącznikiem. */
    public String stanA;
    public String stanB;
    /** Statystyki wieżyczki (opcjonalne; 0/brak = domyślne z ArmorStandTuner: 3.0 obrażeń,
     *  zasięg 8 bloków, strzał co 40 ticków, bez efektu). Ustawia się w pliku presetu. */
    public double damage;
    public double range;
    public int fireInterval;
    /** Strzał wieżyczki zamraża moba (spowolnienie + szron) - np. wieżyczka lodu. */
    public boolean freeze;
    /** Strzał wieżyczki TRUJE moba: własny DoT 1 HP/s przez 8 s (moby są odporne
     *  na waniliową truciznę, więc obrażenia idą poza efektem) - np. wieżyczka trutki. */
    public boolean poison;
    /** Strzał wieżyczki PODPALA moba na 8 s (jak zaklęty ogień / fire aspect) - np. wieżyczka ognia. */
    public boolean fire;
    /** Strzał wieżyczki SKACZE ŁAŃCUCHEM na kolejne moby (100% -> 65% -> 35%, max 3)
     *  z elektryczną wiązką między nimi - np. wieżyczka elektryka. */
    public boolean chain;
    public List<PartData> parts = new ArrayList<>();

    /** Pozycja stojaka w świecie - tylko dla autosave (przywracanie w oryginalne miejsce). */
    public String world;
    public double locX, locY, locZ;

    public static StandPreset fromStand(String name, ArmorStand stand, double sink,
                                        List<ArmorStandTuner.DisplayPart> attached) {
        StandPreset p = new StandPreset();
        p.name = name;
        p.small = stand.isSmall();
        p.arms = stand.hasArms();
        p.visible = stand.isVisible();
        p.base = stand.hasBasePlate();
        p.head = itemName(stand.getEquipment().getHelmet());
        p.hand = itemName(stand.getEquipment().getItemInMainHand());
        p.offhand = itemName(stand.getEquipment().getItemInOffHand());
        p.headX = Math.toDegrees(stand.getHeadPose().getX());
        p.headY = Math.toDegrees(stand.getHeadPose().getY());
        p.headZ = Math.toDegrees(stand.getHeadPose().getZ());
        p.bodyX = Math.toDegrees(stand.getBodyPose().getX());
        p.bodyY = Math.toDegrees(stand.getBodyPose().getY());
        p.bodyZ = Math.toDegrees(stand.getBodyPose().getZ());
        p.rightX = Math.toDegrees(stand.getRightArmPose().getX());
        p.rightY = Math.toDegrees(stand.getRightArmPose().getY());
        p.rightZ = Math.toDegrees(stand.getRightArmPose().getZ());
        p.leftX = Math.toDegrees(stand.getLeftArmPose().getX());
        p.leftY = Math.toDegrees(stand.getLeftArmPose().getY());
        p.leftZ = Math.toDegrees(stand.getLeftArmPose().getZ());
        p.rightLegX = Math.toDegrees(stand.getRightLegPose().getX());
        p.rightLegY = Math.toDegrees(stand.getRightLegPose().getY());
        p.rightLegZ = Math.toDegrees(stand.getRightLegPose().getZ());
        p.leftLegX = Math.toDegrees(stand.getLeftLegPose().getX());
        p.leftLegY = Math.toDegrees(stand.getLeftLegPose().getY());
        p.leftLegZ = Math.toDegrees(stand.getLeftLegPose().getZ());
        p.yaw = stand.getLocation().getYaw();
        p.pitch = stand.getLocation().getPitch();
        p.sink = sink;
        p.seat = stand.getScoreboardTags().contains(SEAT_TAG);
        p.seatName = stand.getPersistentDataContainer().get(ArmorStandTuner.KEY_SEAT_NAME, PersistentDataType.STRING);
        p.hp = ArmorStandTuner.turretMaxHp(stand);
        if (attached != null) {
            for (ArmorStandTuner.DisplayPart part : attached) {
                PartData d = part.toData();
                if (d != null) p.parts.add(d);
            }
        }
        return p;
    }

    /** Nakłada zapisany wygląd na stojak (bez pozycji i części - te ustawia miejsce stawiania). */
    public void applyTo(ArmorStand stand) {
        stand.setSmall(small);
        stand.setArms(arms);
        stand.setVisible(visible);
        stand.setBasePlate(base);
        stand.getEquipment().setHelmet(stack(head));
        stand.getEquipment().setItemInMainHand(stack(hand));
        stand.getEquipment().setItemInOffHand(stack(offhand));
        stand.setHeadPose(pose(headX, headY, headZ));
        stand.setBodyPose(pose(bodyX, bodyY, bodyZ));
        stand.setRightArmPose(pose(rightX, rightY, rightZ));
        stand.setLeftArmPose(pose(leftX, leftY, leftZ));
        stand.setRightLegPose(pose(rightLegX, rightLegY, rightLegZ));
        stand.setLeftLegPose(pose(leftLegX, leftLegY, leftLegZ));
        stand.setCustomName(name);
        stand.setCustomNameVisible(false);
        if (seat) stand.addScoreboardTag(SEAT_TAG);
        if (seatName != null && !seatName.isEmpty()) {
            stand.getPersistentDataContainer().set(ArmorStandTuner.KEY_SEAT_NAME, PersistentDataType.STRING, seatName);
        }
    }

    public boolean save(File file) {
        YamlConfiguration c = new YamlConfiguration();
        c.set("name", name);
        c.set("small", small);
        c.set("arms", arms);
        c.set("visible", visible);
        c.set("base", base);
        c.set("head", head);
        c.set("hand", hand);
        c.set("offhand", offhand);
        writePose(c, "head", headX, headY, headZ);
        writePose(c, "body", bodyX, bodyY, bodyZ);
        writePose(c, "rightArm", rightX, rightY, rightZ);
        writePose(c, "leftArm", leftX, leftY, leftZ);
        writePose(c, "rightLeg", rightLegX, rightLegY, rightLegZ);
        writePose(c, "leftLeg", leftLegX, leftLegY, leftLegZ);
        c.set("yaw", yaw);
        c.set("pitch", pitch);
        c.set("sink", sink);
        c.set("seat", seat);
        if (seatName != null && !seatName.isEmpty()) c.set("seat-name", seatName);
        if (dekoracja) c.set("dekoracja", true);
        if (stanA != null) c.set("stan-a", stanA);
        if (stanB != null) c.set("stan-b", stanB);
        if (hp > 0) c.set("hp", hp);
        if (damage > 0) c.set("damage", damage);
        if (range > 0) c.set("range", range);
        if (fireInterval > 0) c.set("fire-interval", fireInterval);
        if (freeze) c.set("freeze", true);
        if (poison) c.set("poison", true);
        if (fire) c.set("fire", true);
        if (chain) c.set("chain", true);
        List<Map<String, Object>> partList = new ArrayList<>();
        for (PartData d : parts) {
            partList.add(partToMap(d));
        }
        c.set("parts", partList);
        try {
            c.save(file);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Zapisuje LISTĘ presetów do JEDNEGO pliku (sekcja "stojaki") - autosave.
     * Nadpisuje cały plik; każdy stojak to jeden wpis z pozycją (world/locX/locY/locZ).
     */
    public static boolean saveAll(List<StandPreset> presets, File file) {
        YamlConfiguration c = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (StandPreset p : presets) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.name);
            m.put("small", p.small);
            m.put("arms", p.arms);
            m.put("visible", p.visible);
            m.put("base", p.base);
            m.put("head", p.head);
            m.put("hand", p.hand);
            m.put("offhand", p.offhand);
            m.put("headPose", Map.of("x", p.headX, "y", p.headY, "z", p.headZ));
            m.put("bodyPose", Map.of("x", p.bodyX, "y", p.bodyY, "z", p.bodyZ));
            m.put("rightArmPose", Map.of("x", p.rightX, "y", p.rightY, "z", p.rightZ));
            m.put("leftArmPose", Map.of("x", p.leftX, "y", p.leftY, "z", p.leftZ));
            m.put("rightLegPose", Map.of("x", p.rightLegX, "y", p.rightLegY, "z", p.rightLegZ));
            m.put("leftLegPose", Map.of("x", p.leftLegX, "y", p.leftLegY, "z", p.leftLegZ));
            m.put("yaw", p.yaw);
            m.put("pitch", p.pitch);
            m.put("sink", p.sink);
            m.put("seat", p.seat);
            if (p.seatName != null && !p.seatName.isEmpty()) m.put("seat-name", p.seatName);
            if (p.dekoracja) m.put("dekoracja", true);
            if (p.stanA != null) m.put("stan-a", p.stanA);
            if (p.stanB != null) m.put("stan-b", p.stanB);
            if (p.hp > 0) m.put("hp", p.hp);
            if (p.damage > 0) m.put("damage", p.damage);
            if (p.range > 0) m.put("range", p.range);
            if (p.fireInterval > 0) m.put("fire-interval", p.fireInterval);
            if (p.freeze) m.put("freeze", true);
            if (p.poison) m.put("poison", true);
            if (p.fire) m.put("fire", true);
            if (p.chain) m.put("chain", true);
            List<Map<String, Object>> partList = new ArrayList<>();
            for (PartData d : p.parts) partList.add(partToMap(d));
            m.put("parts", partList);
            if (p.world != null) {
                m.put("world", p.world);
                m.put("locX", p.locX);
                m.put("locY", p.locY);
                m.put("locZ", p.locZ);
            }
            list.add(m);
        }
        c.set("stojaki", list);
        try {
            c.save(file);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Wczytuje listę presetów z jednego pliku (autosave). Pusta lista, gdy brak pliku. */
    public static List<StandPreset> loadAll(File file) {
        List<StandPreset> out = new ArrayList<>();
        if (file == null || !file.isFile()) return out;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        List<?> raw = c.getList("stojaki");
        if (raw == null) return out;
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> map)) continue;
            StandPreset p = new StandPreset();
            p.name = str(map, "name", "stojak");
            p.small = bool(map, "small", false);
            p.arms = bool(map, "arms", true);
            p.visible = bool(map, "visible", false);
            p.base = bool(map, "base", false);
            p.head = str(map, "head", "AIR");
            p.hand = str(map, "hand", "AIR");
            p.offhand = str(map, "offhand", "AIR");
            p.headX = mapDouble(map, "headPose", "x", 0);
            p.headY = mapDouble(map, "headPose", "y", 0);
            p.headZ = mapDouble(map, "headPose", "z", 0);
            p.bodyX = mapDouble(map, "bodyPose", "x", 0);
            p.bodyY = mapDouble(map, "bodyPose", "y", 0);
            p.bodyZ = mapDouble(map, "bodyPose", "z", 0);
            p.rightX = mapDouble(map, "rightArmPose", "x", 0);
            p.rightY = mapDouble(map, "rightArmPose", "y", 0);
            p.rightZ = mapDouble(map, "rightArmPose", "z", 0);
            p.leftX = mapDouble(map, "leftArmPose", "x", 0);
            p.leftY = mapDouble(map, "leftArmPose", "y", 0);
            p.leftZ = mapDouble(map, "leftArmPose", "z", 0);
            p.rightLegX = mapDouble(map, "rightLegPose", "x", 0);
            p.rightLegY = mapDouble(map, "rightLegPose", "y", 0);
            p.rightLegZ = mapDouble(map, "rightLegPose", "z", 0);
            p.leftLegX = mapDouble(map, "leftLegPose", "x", 0);
            p.leftLegY = mapDouble(map, "leftLegPose", "y", 0);
            p.leftLegZ = mapDouble(map, "leftLegPose", "z", 0);
            p.yaw = topDouble(map, "yaw", 0);
            p.pitch = topDouble(map, "pitch", 0);
            p.sink = normalizeSink(topDouble(map, "sink", -1.0));
            p.seat = bool(map, "seat", false);
            p.seatName = str(map, "seat-name", null);
            p.dekoracja = bool(map, "dekoracja", false);
            p.stanA = str(map, "stan-a", null);
            p.stanB = str(map, "stan-b", null);
            p.hp = topDouble(map, "hp", 0);
            p.damage = topDouble(map, "damage", 0);
            p.range = topDouble(map, "range", 0);
            p.fireInterval = topInt(map, "fire-interval", 0);
            p.freeze = bool(map, "freeze", false);
            p.poison = bool(map, "poison", false);
            p.fire = bool(map, "fire", false);
            p.chain = bool(map, "chain", false);
            p.world = str(map, "world", null);
            if (p.world != null) {
                p.locX = topDouble(map, "locX", 0);
                p.locY = topDouble(map, "locY", 0);
                p.locZ = topDouble(map, "locZ", 0);
            }
            Object partsObj = map.get("parts");
            if (partsObj instanceof List<?> rawParts) {
                for (Object po : rawParts) {
                    if (!(po instanceof Map<?, ?> pmap)) continue;
                    PartData d = new PartData();
                    Object mat = pmap.get("material");
                    d.material = mat != null ? String.valueOf(mat) : "STONE";
                    d.scaleX = mapDouble(pmap, "scale", "x", 1);
                    d.scaleY = mapDouble(pmap, "scale", "y", 1);
                    d.scaleZ = mapDouble(pmap, "scale", "z", 1);
                    d.rotX = mapDouble(pmap, "rot", "x", 0);
                    d.rotY = mapDouble(pmap, "rot", "y", 0);
                    d.rotZ = mapDouble(pmap, "rot", "z", 0);
                    d.tx = mapDouble(pmap, "trans", "x", 0);
                    d.ty = mapDouble(pmap, "trans", "y", 0);
                    d.tz = mapDouble(pmap, "trans", "z", 0);
                    d.turret = bool(pmap, "turret", false);
                    d.bright = bool(pmap, "bright", false);
                    Object nm = pmap.get("name");
                    if (nm != null && !String.valueOf(nm).isEmpty()) d.name = String.valueOf(nm);
                    p.parts.add(d);
                }
            }
            out.add(p);
        }
        return out;
    }

    public static StandPreset load(File file) {
        if (file == null || !file.isFile()) return null;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        StandPreset p = new StandPreset();
        p.name = c.getString("name", "stojak");
        p.small = c.getBoolean("small", false);
        p.arms = c.getBoolean("arms", true);
        p.visible = c.getBoolean("visible", false);
        p.base = c.getBoolean("base", false);
        p.head = c.getString("head", "AIR");
        p.hand = c.getString("hand", "AIR");
        p.offhand = c.getString("offhand", "AIR");
        double[] h = readPose(c, "head");
        p.headX = h[0];
        p.headY = h[1];
        p.headZ = h[2];
        double[] b = readPose(c, "body");
        p.bodyX = b[0];
        p.bodyY = b[1];
        p.bodyZ = b[2];
        double[] r = readPose(c, "rightArm");
        p.rightX = r[0];
        p.rightY = r[1];
        p.rightZ = r[2];
        double[] l = readPose(c, "leftArm");
        p.leftX = l[0];
        p.leftY = l[1];
        p.leftZ = l[2];
        double[] rl = readPose(c, "rightLeg");
        p.rightLegX = rl[0];
        p.rightLegY = rl[1];
        p.rightLegZ = rl[2];
        double[] ll = readPose(c, "leftLeg");
        p.leftLegX = ll[0];
        p.leftLegY = ll[1];
        p.leftLegZ = ll[2];
        p.yaw = c.getDouble("yaw", 0);
        p.pitch = c.getDouble("pitch", 0);
        p.sink = normalizeSink(c.getDouble("sink", -1.0));
        p.seat = c.getBoolean("seat", false);
        p.seatName = c.getString("seat-name", null);
        p.dekoracja = c.getBoolean("dekoracja", false);
        p.stanA = c.getString("stan-a", null);
        p.stanB = c.getString("stan-b", null);
        p.hp = c.getDouble("hp", 0);
        p.damage = c.getDouble("damage", 0);
        p.range = c.getDouble("range", 0);
        p.fireInterval = c.getInt("fire-interval", 0);
        p.freeze = c.getBoolean("freeze", false);
        p.poison = c.getBoolean("poison", false);
        p.fire = c.getBoolean("fire", false);
        p.chain = c.getBoolean("chain", false);
        List<?> rawParts = c.getList("parts");
        if (rawParts != null) {
            for (Object o : rawParts) {
                if (!(o instanceof Map<?, ?> map)) continue;
                PartData d = new PartData();
                Object mat = map.get("material");
                d.material = mat != null ? String.valueOf(mat) : "STONE";
                d.scaleX = mapDouble(map, "scale", "x", 1);
                d.scaleY = mapDouble(map, "scale", "y", 1);
                d.scaleZ = mapDouble(map, "scale", "z", 1);
                d.rotX = mapDouble(map, "rot", "x", 0);
                d.rotY = mapDouble(map, "rot", "y", 0);
                d.rotZ = mapDouble(map, "rot", "z", 0);
                d.tx = mapDouble(map, "trans", "x", 0);
                d.ty = mapDouble(map, "trans", "y", 0);
                d.tz = mapDouble(map, "trans", "z", 0);
                d.turret = bool(map, "turret", false); // flaga części obrotowej wieżyczki - bez niej góra po respawnie nie kręciłaby się
                d.bright = bool(map, "bright", false); // jasność części (ŚWIATŁO) - bez niej po respawnie część nie świeciłaby
                Object nm = map.get("name");
                if (nm != null && !String.valueOf(nm).isEmpty()) d.name = String.valueOf(nm);
                p.parts.add(d);
            }
        }
        return p;
    }

    private static Map<String, Object> partToMap(PartData d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("material", d.material);
        m.put("scale", Map.of("x", d.scaleX, "y", d.scaleY, "z", d.scaleZ));
        m.put("rot", Map.of("x", d.rotX, "y", d.rotY, "z", d.rotZ));
        m.put("trans", Map.of("x", d.tx, "y", d.ty, "z", d.tz));
        if (d.turret) m.put("turret", true);
        if (d.bright) m.put("bright", true);
        if (d.name != null && !d.name.isEmpty()) m.put("name", d.name);
        return m;
    }

    private static String str(Map<?, ?> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    private static boolean bool(Map<?, ?> map, String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return Boolean.parseBoolean(String.valueOf(v));
        return def;
    }

    private static double topDouble(Map<?, ?> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static int topInt(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static double mapDouble(Map<?, ?> map, String section, String key, double def) {
        Object sec = map.get(section);
        if (sec instanceof Map<?, ?> m) {
            Object v = m.get(key);
            if (v instanceof Number n) return n.doubleValue();
            if (v != null) {
                try {
                    return Double.parseDouble(String.valueOf(v));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return def;
    }

    private static void writePose(YamlConfiguration c, String prefix, double x, double y, double z) {
        c.set(prefix + ".x", x);
        c.set(prefix + ".y", y);
        c.set(prefix + ".z", z);
    }

    private static double[] readPose(YamlConfiguration c, String prefix) {
        return new double[]{
                c.getDouble(prefix + ".x", 0),
                c.getDouble(prefix + ".y", 0),
                c.getDouble(prefix + ".z", 0)
        };
    }

    private static ItemStack stack(String materialName) {
        Material m = Material.matchMaterial(materialName);
        return m == null ? new ItemStack(Material.AIR) : new ItemStack(m);
    }

    private static EulerAngle pose(double x, double y, double z) {
        return new EulerAngle(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
    }

    private static String itemName(ItemStack item) {
        return item == null || item.getType() == Material.AIR ? "AIR" : item.getType().name();
    }
}
