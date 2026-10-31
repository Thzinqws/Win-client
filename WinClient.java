package com.winclient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

@Mod(modid = "winclient", name = "Winclient", version = "2.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class WinClient {

    static final Minecraft mc = Minecraft.getMinecraft();
    static final List<Feature> features = new ArrayList<Feature>();
    static final Properties props = new Properties();
    static File cfgFile;
    static KeyBinding kOpen, kZoom, kPersp, kText;
    static final List<Long> lc = new ArrayList<Long>();
    static final List<Long> rc = new ArrayList<Long>();
    static final long startTime = System.currentTimeMillis();
    static boolean openNext;
    static double speed;
    static AutoText autoText;

    // ------------------------------------------------------------------ init

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        cfgFile = new File(mc.mcDataDir, "config/winclient.properties");
        kOpen = new KeyBinding("Abrir menu", Keyboard.KEY_RSHIFT, "Winclient");
        kZoom = new KeyBinding("Zoom", Keyboard.KEY_C, "Winclient");
        kPersp = new KeyBinding("Perspective", Keyboard.KEY_V, "Winclient");
        kText = new KeyBinding("Auto Text", Keyboard.KEY_NONE, "Winclient");
        ClientRegistry.registerKeyBinding(kOpen);
        ClientRegistry.registerKeyBinding(kZoom);
        ClientRegistry.registerKeyBinding(kPersp);
        ClientRegistry.registerKeyBinding(kText);

        autoText = new AutoText();
        features.add(new Fps());
        features.add(new Cps());
        features.add(new Ping());
        features.add(new Coords());
        features.add(new Direction());
        features.add(new Memory());
        features.add(new PlayTime());
        features.add(new ServerAddress());
        features.add(new Speedometer());
        features.add(new TimeDisplay());
        features.add(new Reach());
        features.add(new Combo());
        features.add(new PotionCounter());
        features.add(new ToggleSprint());
        features.add(new Keystrokes());
        features.add(new ArmorHud());
        features.add(new PotionEffectsHud());
        features.add(new TntTimer());
        features.add(new Hitbox());
        features.add(new FullBright());
        features.add(new Zoom());
        features.add(new Perspective());
        features.add(new Nametags());
        features.add(new Particles());
        features.add(new TimeChanger());
        features.add(new WeatherChanger());
        features.add(new AutoGG());
        features.add(autoText);
        features.add(new BlockOverlay());
        features.add(new Crosshair());
        features.add(new HurtCamOff());
        features.add(new OldAnimations());
        features.add(new Bossbar());
        features.add(new FpsBoost());
        Collections.sort(features, new Comparator<Feature>() {
            public int compare(Feature a, Feature b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        load();

        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        ClientCommandHandler.instance.registerCommand(new CmdWc());
    }

    static class CmdWc extends CommandBase {
        public String getCommandName() { return "wc"; }
        public String getCommandUsage(ICommandSender s) { return "/wc"; }
        public void processCommand(ICommandSender s, String[] a) { openNext = true; }
        public int getRequiredPermissionLevel() { return 0; }
    }

    // ---------------------------------------------------------------- config

    static void load() {
        try {
            if (cfgFile.exists()) {
                FileInputStream in = new FileInputStream(cfgFile);
                props.load(in);
                in.close();
            }
        } catch (Exception ex) { }
        for (Feature f : features) {
            String k = f.key();
            f.enabled = Boolean.parseBoolean(props.getProperty(k + ".enabled", String.valueOf(f.enabled)));
            for (Setting s : f.settings) {
                String v = props.getProperty(k + "." + s.name.replace(' ', '_'));
                if (v == null) continue;
                try {
                    if (s.isBool) s.b = Boolean.parseBoolean(v); else s.v = Float.parseFloat(v);
                } catch (Exception ex) { }
            }
            if (f instanceof HudFeature) {
                HudFeature h = (HudFeature) f;
                try {
                    h.x = Integer.parseInt(props.getProperty(k + ".x", String.valueOf(h.x)));
                    h.y = Integer.parseInt(props.getProperty(k + ".y", String.valueOf(h.y)));
                } catch (Exception ex) { }
            }
        }
    }

    static void save() {
        for (Feature f : features) {
            String k = f.key();
            props.setProperty(k + ".enabled", String.valueOf(f.enabled));
            for (Setting s : f.settings) {
                props.setProperty(k + "." + s.name.replace(' ', '_'), s.isBool ? String.valueOf(s.b) : String.valueOf(s.v));
            }
            if (f instanceof HudFeature) {
                HudFeature h = (HudFeature) f;
                props.setProperty(k + ".x", String.valueOf(h.x));
                props.setProperty(k + ".y", String.valueOf(h.y));
            }
        }
        try {
            cfgFile.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(cfgFile);
            props.store(out, "Winclient");
            out.close();
        } catch (IOException ex) { }
    }

    // ---------------------------------------------------------------- events

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (openNext && mc.currentScreen == null) {
            openNext = false;
            mc.displayGuiScreen(new ClickGui());
        }
        if (mc.thePlayer == null || mc.theWorld == null) return;
        double dx = mc.thePlayer.posX - mc.thePlayer.prevPosX;
        double dz = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
        speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
        for (Feature f : features) {
            f.ensureApplied();
            if (f.enabled) f.onTick();
        }
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (kOpen.isPressed()) mc.displayGuiScreen(new ClickGui());
        if (kText.isPressed() && autoText.enabled && mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage(props.getProperty("autotext.message", "gl hf"));
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (!e.buttonstate) return;
        long n = System.currentTimeMillis();
        if (e.button == 0) lc.add(n);
        else if (e.button == 1) rc.add(n);
    }

    @SubscribeEvent
    public void onPre(RenderGameOverlayEvent.Pre e) {
        for (Feature f : features) if (f.enabled) f.onOverlayPre(e);
    }

    @SubscribeEvent
    public void onPost(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (mc.thePlayer == null || mc.gameSettings.hideGUI) return;
        for (Feature f : features) {
            if (f.enabled && f instanceof HudFeature) {
                HudFeature h = (HudFeature) f;
                GlStateManager.pushMatrix();
                h.draw(h.x, h.y);
                GlStateManager.popMatrix();
                GlStateManager.color(1f, 1f, 1f, 1f);
            }
        }
    }

    @SubscribeEvent
    public void onFov(FOVUpdateEvent e) {
        for (Feature f : features) if (f.enabled) f.onFov(e);
    }

    @SubscribeEvent
    public void onHighlight(DrawBlockHighlightEvent e) {
        for (Feature f : features) if (f.enabled) f.onHighlight(e);
    }

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent e) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        for (Feature f : features) if (f.enabled) f.onWorld(e.partialTicks);
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (e.type == 2) return;
        String m = e.message.getUnformattedText();
        for (Feature f : features) if (f.enabled) f.onChat(m);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        if (e.entityPlayer != mc.thePlayer) return;
        for (Feature f : features) if (f.enabled) f.onAttack(e.target);
    }

    // --------------------------------------------------------------- helpers

    static FontRenderer fr() { return mc.fontRendererObj; }

    static int cps(List<Long> l) {
        long n = System.currentTimeMillis();
        Iterator<Long> it = l.iterator();
        while (it.hasNext()) if (n - it.next() > 1000) it.remove();
        return l.size();
    }

    static void drawWorldText(String text, double x, double y, double z, float scale) {
        RenderManager rm = mc.getRenderManager();
        FontRenderer f = mc.fontRendererObj;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x - rm.viewerPosX, y - rm.viewerPosY, z - rm.viewerPosZ);
        GL11.glNormal3f(0f, 1f, 0f);
        GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int w = f.getStringWidth(text) / 2;
        Gui.drawRect(-w - 2, -2, w + 2, 9, 0x66000000);
        f.drawStringWithShadow(text, -w, 0, 0xFFFFFFFF);
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------ base types

    static class Setting {
        final String name;
        final boolean isBool;
        boolean b;
        float v, min, max;
        Setting(String n, boolean d) { name = n; isBool = true; b = d; }
        Setting(String n, float d, float mn, float mx) { name = n; isBool = false; v = d; min = mn; max = mx; }
    }

    static abstract class Feature {
        final String name, desc;
        boolean enabled, applied;
        final List<Setting> settings = new ArrayList<Setting>();

        Feature(String n, String d) { name = n; desc = d; }
        String key() { return name.replace(' ', '_'); }
        Setting bool(String n, boolean d) { Setting s = new Setting(n, d); settings.add(s); return s; }
        Setting num(String n, float d, float mn, float mx) { Setting s = new Setting(n, d, mn, mx); settings.add(s); return s; }
        void ensureApplied() {
            if (!applied) { applied = true; if (enabled) onEnable(); }
        }
        void toggle() {
            ensureApplied();
            enabled = !enabled;
            if (enabled) onEnable(); else onDisable();
        }
        void onEnable() { }
        void onDisable() { }
        void onTick() { }
        void onWorld(float pt) { }
        void onChat(String msg) { }
        void onAttack(Entity target) { }
        void onFov(FOVUpdateEvent e) { }
        void onHighlight(DrawBlockHighlightEvent e) { }
        void onOverlayPre(RenderGameOverlayEvent.Pre e) { }
    }

    static abstract class HudFeature extends Feature {
        int x, y, w = 60, h = 14;
        final Setting bg;
        HudFeature(String n, String d, int x, int y, boolean on) {
            super(n, d);
            this.x = x; this.y = y; this.enabled = on;
            bg = bool("Fundo", true);
        }
        abstract void draw(int x, int y);
        void box(int x, int y, int w, int h) {
            this.w = w; this.h = h;
            if (bg.b) Gui.drawRect(x, y, x + w, y + h, 0x66000000);
        }
    }

    static abstract class TextHud extends HudFeature {
        TextHud(String n, String d, int x, int y, boolean on) { super(n, d, x, y, on); }
        abstract String text();
        void draw(int x, int y) {
            String s = text();
            box(x, y, fr().getStringWidth(s) + 8, 14);
            fr().drawStringWithShadow(s, x + 4, y + 3, 0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------- HUD mods

    static class Fps extends TextHud {
        Fps() { super("FPS", "Mostra os FPS", 4, 4, true); }
        String text() { return "FPS: " + Minecraft.getDebugFPS(); }
    }

    static class Cps extends TextHud {
        Cps() { super("CPS", "Cliques por segundo", 4, 20, true); }
        String text() { return "CPS: " + cps(lc) + " | " + cps(rc); }
    }

    static class Ping extends TextHud {
        int ping;
        Ping() { super("Ping Display", "Mostra o ping", 4, 36, true); }
        void onTick() {
            NetHandlerPlayClient nh = mc.getNetHandler();
            if (nh != null && mc.thePlayer != null) {
                NetworkPlayerInfo i = nh.getPlayerInfo(mc.thePlayer.getUniqueID());
                if (i != null) ping = i.getResponseTime();
            }
        }
        String text() { return "Ping: " + ping + " ms"; }
    }

    static class Coords extends TextHud {
        Coords() { super("Coordinates", "Coordenadas XYZ", 4, 52, true); }
        String text() {
            if (mc.thePlayer == null) return "XYZ";
            return "XYZ: " + (int) Math.floor(mc.thePlayer.posX) + " / " + (int) Math.floor(mc.thePlayer.posY) + " / " + (int) Math.floor(mc.thePlayer.posZ);
        }
    }

    static class Direction extends TextHud {
        final String[] d = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        Direction() { super("Direction Hud", "Direcao que voce olha", 4, 68, false); }
        String text() {
            if (mc.thePlayer == null) return "Dir";
            float yaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw);
            int i = ((Math.round(yaw / 45f) % 8) + 8) % 8;
            return "Dir: " + d[i];
        }
    }

    static class Memory extends TextHud {
        Memory() { super("Memory", "Uso de memoria", 4, 84, false); }
        String text() {
            Runtime r = Runtime.getRuntime();
            long used = (r.totalMemory() - r.freeMemory()) / 1048576L;
            long max = r.maxMemory() / 1048576L;
            return "Mem: " + used + "/" + max + " MB";
        }
    }

    static class PlayTime extends TextHud {
        PlayTime() { super("Play Time", "Tempo de jogo na sessao", 4, 100, false); }
        String text() {
            long s = (System.currentTimeMillis() - startTime) / 1000L;
            return String.format("Tempo: %02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        }
    }

    static class ServerAddress extends TextHud {
        ServerAddress() { super("Server Address", "IP do servidor", 4, 116, false); }
        String text() {
            return mc.getCurrentServerData() == null ? "Singleplayer" : mc.getCurrentServerData().serverIP;
        }
    }

    static class Speedometer extends TextHud {
        Speedometer() { super("Speedometer", "Velocidade em blocos/s", 4, 132, false); }
        String text() { return String.format("%.2f b/s", speed); }
    }

    static class TimeDisplay extends TextHud {
        final SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss");
        TimeDisplay() { super("Time Display", "Hora real", 4, 148, false); }
        String text() { return f.format(new Date()); }
    }

    static class Reach extends TextHud {
        double reach;
        Reach() { super("Reach Display", "Distancia do ultimo hit", 4, 164, false); }
        void onAttack(Entity t) {
            if (mc.objectMouseOver != null && mc.objectMouseOver.hitVec != null && mc.thePlayer != null) {
                reach = mc.objectMouseOver.hitVec.distanceTo(mc.thePlayer.getPositionEyes(1f));
            }
        }
        String text() { return String.format("Reach: %.2f", reach); }
    }

    static class Combo extends TextHud {
        int combo;
        long last;
        float lastHealth;
        Combo() { super("Combo Display", "Hits seguidos", 4, 180, false); }
        void onAttack(Entity t) { combo++; last = System.currentTimeMillis(); }
        void onTick() {
            if (mc.thePlayer == null) return;
            float hp = mc.thePlayer.getHealth();
            if (hp < lastHealth) combo = 0;
            lastHealth = hp;
            if (System.currentTimeMillis() - last > 3000) combo = 0;
        }
        String text() { return "Combo: " + combo; }
    }

    static class PotionCounter extends TextHud {
        PotionCounter() { super("Potion Counter", "Pots de vida no inventario", 4, 196, false); }
        String text() {
            int n = 0;
            if (mc.thePlayer != null) {
                for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
                    if (s != null && s.getItem() instanceof ItemPotion && ItemPotion.isSplash(s.getMetadata())) {
                        List<PotionEffect> fx = ((ItemPotion) s.getItem()).getEffects(s);
                        if (fx != null) {
                            for (PotionEffect pe : fx) {
                                if (pe.getPotionID() == Potion.heal.id) { n += s.stackSize; break; }
                            }
                        }
                    }
                }
            }
            return "Pots: " + n;
        }
    }

    static class ToggleSprint extends TextHud {
        ToggleSprint() { super("Toggle Sprint", "Sprint automatico", 4, 68, true); }
        void onTick() {
            if (mc.currentScreen == null) KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
        void onDisable() { KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false); }
        String text() { return "[Sprint: Toggled]"; }
    }

    static class Keystrokes extends HudFeature {
        Keystrokes() { super("Keystrokes", "Mostra WASD e cliques", 4, 90, true); }
        void key(int x, int y, int w, int hh, String label, boolean down) {
            Gui.drawRect(x, y, x + w, y + hh, down ? 0xAAFFFFFF : 0x66000000);
            fr().drawString(label, x + (w - fr().getStringWidth(label)) / 2, y + hh / 2 - 4, down ? 0xFF000000 : 0xFFFFFFFF);
        }
        void draw(int x, int y) {
            w = 71; h = 70;
            key(x + 24, y, 22, 22, "W", mc.gameSettings.keyBindForward.isKeyDown());
            key(x, y + 24, 22, 22, "A", mc.gameSettings.keyBindLeft.isKeyDown());
            key(x + 24, y + 24, 22, 22, "S", mc.gameSettings.keyBindBack.isKeyDown());
            key(x + 48, y + 24, 22, 22, "D", mc.gameSettings.keyBindRight.isKeyDown());
            key(x, y + 48, 35, 22, "LMB " + cps(lc), mc.gameSettings.keyBindAttack.isKeyDown());
            key(x + 36, y + 48, 35, 22, "RMB " + cps(rc), mc.gameSettings.keyBindUseItem.isKeyDown());
        }
    }

    static class ArmorHud extends HudFeature {
        ArmorHud() { super("Armor HUD", "Armadura e item na mao", 4, 170, true); }
        void draw(int x, int y) {
            List<ItemStack> list = new ArrayList<ItemStack>();
            for (int i = 3; i >= 0; i--) {
                ItemStack s = mc.thePlayer.inventory.armorItemInSlot(i);
                if (s != null) list.add(s);
            }
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held != null) list.add(held);
            int hh = Math.max(list.size() * 18, 18);
            box(x, y, 64, hh);
            if (list.isEmpty()) { fr().drawStringWithShadow("Armor", x + 4, y + 5, 0xFFAAAAAA); return; }
            for (int i = 0; i < list.size(); i++) {
                ItemStack s = list.get(i);
                if (s.getMaxDamage() > 0) {
                    fr().drawStringWithShadow(String.valueOf(s.getMaxDamage() - s.getItemDamage()), x + 24, y + i * 18 + 5, 0xFFFFFFFF);
                }
            }
            RenderHelper.enableGUIStandardItemLighting();
            for (int i = 0; i < list.size(); i++) {
                ItemStack s = list.get(i);
                mc.getRenderItem().renderItemAndEffectIntoGUI(s, x + 2, y + i * 18 + 1);
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, s, x + 2, y + i * 18 + 1, null);
            }
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    static class PotionEffectsHud extends HudFeature {
        PotionEffectsHud() { super("Potion Effects", "Efeitos ativos", 4, 250, true); }
        void draw(int x, int y) {
            List<String> lines = new ArrayList<String>();
            Collection<PotionEffect> fx = mc.thePlayer.getActivePotionEffects();
            for (PotionEffect pe : fx) {
                lines.add(I18n.format(pe.getEffectName()) + " " + (pe.getAmplifier() + 1) + " (" + Potion.getDurationString(pe) + ")");
            }
            if (lines.isEmpty()) lines.add("Sem efeitos");
            int mw = 0;
            for (String s : lines) mw = Math.max(mw, fr().getStringWidth(s));
            box(x, y, mw + 8, lines.size() * 11 + 4);
            for (int i = 0; i < lines.size(); i++) fr().drawStringWithShadow(lines.get(i), x + 4, y + 3 + i * 11, 0xFFFFFFFF);
        }
    }

    // ----------------------------------------------------------- PvP / world

    static class TntTimer extends Feature {
        TntTimer() { super("TNT Timer", "Tempo para a TNT explodir"); }
        void onWorld(float pt) {
            for (Object o : mc.theWorld.loadedEntityList) {
                if (o instanceof EntityTNTPrimed) {
                    EntityTNTPrimed t = (EntityTNTPrimed) o;
                    double x = t.lastTickPosX + (t.posX - t.lastTickPosX) * pt;
                    double y = t.lastTickPosY + (t.posY - t.lastTickPosY) * pt;
                    double z = t.lastTickPosZ + (t.posZ - t.lastTickPosZ) * pt;
                    drawWorldText("\u00a7c" + String.format("%.2f", t.fuse / 20.0), x, y + 1.2, z, 0.04f);
                }
            }
        }
    }

    static class Hitbox extends Feature {
        Hitbox() { super("Hitbox", "Mostra as hitboxes (como F3+B)"); }
        void onEnable() { if (mc.getRenderManager() != null) mc.getRenderManager().setDebugBoundingBox(true); }
        void onDisable() { if (mc.getRenderManager() != null) mc.getRenderManager().setDebugBoundingBox(false); }
    }

    static class FullBright extends Feature {
        float old = 1f;
        FullBright() { super("Full Bright", "Brilho maximo"); }
        void onEnable() { old = mc.gameSettings.gammaSetting; }
        void onTick() { mc.gameSettings.gammaSetting = 100f; }
        void onDisable() { mc.gameSettings.gammaSetting = old; }
    }

    static class Zoom extends Feature {
        final Setting factor;
        boolean zooming;
        float oldSens;
        Zoom() { super("Zoom", "Segure a tecla C para dar zoom"); factor = num("Fator", 4f, 1.5f, 10f); }
        void onTick() {
            boolean z = kZoom.isKeyDown() && mc.currentScreen == null;
            if (z && !zooming) {
                oldSens = mc.gameSettings.mouseSensitivity;
                mc.gameSettings.mouseSensitivity = oldSens / factor.v;
            } else if (!z && zooming) {
                mc.gameSettings.mouseSensitivity = oldSens;
            }
            zooming = z;
        }
        void onDisable() {
            if (zooming) { mc.gameSettings.mouseSensitivity = oldSens; zooming = false; }
        }
        void onFov(FOVUpdateEvent e) { if (zooming) e.newfov = e.newfov / factor.v; }
    }

    static class Perspective extends Feature {
        boolean on;
        int old;
        Perspective() { super("Perspective", "Segure V para ver em 3a pessoa"); }
        void onTick() {
            boolean h = kPersp.isKeyDown() && mc.currentScreen == null;
            if (h && !on) { old = mc.gameSettings.thirdPersonView; mc.gameSettings.thirdPersonView = 1; }
            else if (!h && on) { mc.gameSettings.thirdPersonView = old; }
            on = h;
        }
        void onDisable() { if (on) { mc.gameSettings.thirdPersonView = old; on = false; } }
    }

    static class Nametags extends Feature {
        Nametags() { super("Nametags", "Mostra seu proprio nome em 3a pessoa"); }
        void onWorld(float pt) {
            if (mc.gameSettings.thirdPersonView == 0) return;
            Entity p = mc.thePlayer;
            double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
            double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt;
            double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
            drawWorldText(mc.thePlayer.getName(), x, y + p.height + 0.5, z, 0.025f);
        }
    }

    static class Particles extends Feature {
        int old;
        Particles() { super("Particles", "Reduz particulas ao minimo"); }
        void onEnable() { old = mc.gameSettings.particleSetting; }
        void onTick() { mc.gameSettings.particleSetting = 2; }
        void onDisable() { mc.gameSettings.particleSetting = old; }
    }

    static class TimeChanger extends Feature {
        final Setting time;
        TimeChanger() { super("Time Changer", "Muda a hora do mundo (so para voce)"); time = num("Hora", 6000f, 0f, 24000f); }
        void onTick() { mc.theWorld.setWorldTime((long) time.v); }
    }

    static class WeatherChanger extends Feature {
        final Setting rain;
        WeatherChanger() { super("Weather Changer", "Tira ou poe chuva (so para voce)"); rain = bool("Chuva", false); }
        void onTick() {
            if (rain.b) { mc.theWorld.setRainStrength(1f); }
            else { mc.theWorld.setRainStrength(0f); mc.theWorld.setThunderStrength(0f); }
        }
    }

    static class AutoGG extends Feature {
        final String[] triggers = {"game over", "you won", "winner", "venceu", "vencedor", "fim de jogo", "vitoria", "vit\u00f3ria"};
        long cooldown;
        int pending = -1;
        AutoGG() { super("AutoGG", "Manda gg no fim da partida"); }
        void onChat(String msg) {
            String m = msg.toLowerCase();
            if (System.currentTimeMillis() < cooldown) return;
            for (String t : triggers) {
                if (m.contains(t)) { pending = 30; cooldown = System.currentTimeMillis() + 15000L; return; }
            }
        }
        void onTick() {
            if (pending > 0 && --pending == 0 && mc.thePlayer != null) mc.thePlayer.sendChatMessage("gg");
        }
    }

    static class AutoText extends Feature {
        AutoText() { super("AutoText", "Tecla (Controles > Winclient) manda uma mensagem. Edite autotext.message em config/winclient.properties"); }
    }

    static class BlockOverlay extends Feature {
        final Setting r, g, b, a, width;
        BlockOverlay() {
            super("Block Overlay", "Contorno colorido no bloco mirado");
            r = num("Vermelho", 255f, 0f, 255f);
            g = num("Verde", 255f, 0f, 255f);
            b = num("Azul", 255f, 0f, 255f);
            a = num("Opacidade", 200f, 0f, 255f);
            width = num("Espessura", 2f, 1f, 6f);
        }
        void onHighlight(DrawBlockHighlightEvent e) {
            if (e.target == null || e.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
            e.setCanceled(true);
            BlockPos p = e.target.getBlockPos();
            Block bl = mc.theWorld.getBlockState(p).getBlock();
            bl.setBlockBoundsBasedOnState(mc.theWorld, p);
            double dx = e.player.lastTickPosX + (e.player.posX - e.player.lastTickPosX) * e.partialTicks;
            double dy = e.player.lastTickPosY + (e.player.posY - e.player.lastTickPosY) * e.partialTicks;
            double dz = e.player.lastTickPosZ + (e.player.posZ - e.player.lastTickPosZ) * e.partialTicks;
            AxisAlignedBB bb = bl.getSelectedBoundingBox(mc.theWorld, p).expand(0.002, 0.002, 0.002).offset(-dx, -dy, -dz);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture2D();
            GlStateManager.depthMask(false);
            GL11.glLineWidth(width.v);
            RenderGlobal.drawOutlinedBoundingBox(bb, (int) r.v, (int) g.v, (int) b.v, (int) a.v);
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
        }
    }

    static class Crosshair extends Feature {
        final Setting size, gap, thick, r, g, b;
        Crosshair() {
            super("Crosshair", "Mira personalizada");
            size = num("Tamanho", 5f, 1f, 15f);
            gap = num("Espaco", 3f, 0f, 10f);
            thick = num("Espessura", 2f, 1f, 5f);
            r = num("Vermelho", 255f, 0f, 255f);
            g = num("Verde", 255f, 0f, 255f);
            b = num("Azul", 255f, 0f, 255f);
        }
        void onOverlayPre(RenderGameOverlayEvent.Pre e) {
            if (e.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
            e.setCanceled(true);
            if (mc.gameSettings.thirdPersonView != 0) return;
            int cx = e.resolution.getScaledWidth() / 2;
            int cy = e.resolution.getScaledHeight() / 2;
            int s = (int) size.v, gp = (int) gap.v, t = (int) thick.v, o = t / 2;
            int col = 0xFF000000 | ((int) r.v << 16) | ((int) g.v << 8) | (int) b.v;
            Gui.drawRect(cx - gp - s, cy - o, cx - gp, cy - o + t, col);
            Gui.drawRect(cx + gp, cy - o, cx + gp + s, cy - o + t, col);
            Gui.drawRect(cx - o, cy - gp - s, cx - o + t, cy - gp, col);
            Gui.drawRect(cx - o, cy + gp, cx - o + t, cy + gp + s, col);
        }
    }

    static class HurtCamOff extends Feature {
        HurtCamOff() { super("Hurt Cam Off", "Tira o tremor da camera ao tomar dano"); }
        void onTick() { if (mc.thePlayer != null) mc.thePlayer.hurtTime = 0; }
    }

    static class OldAnimations extends Feature {
        OldAnimations() { super("Old Animations", "Balanca o braco ao bloquear + minerar (estilo 1.7)"); }
        void onTick() {
            if (mc.gameSettings.keyBindAttack.isKeyDown() && mc.thePlayer.isUsingItem()
                    && mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                mc.thePlayer.swingItem();
            }
        }
    }

    static class Bossbar extends Feature {
        Bossbar() { super("Bossbar", "Esconde a barra de boss"); }
        void onOverlayPre(RenderGameOverlayEvent.Pre e) {
            if (e.type == RenderGameOverlayEvent.ElementType.BOSSHEALTH) e.setCanceled(true);
        }
    }

    static class FpsBoost extends Feature {
        boolean fancy, shadows;
        int ao, part;
        FpsBoost() { super("FPS Boost", "Graficos simples, sem sombras e poucas particulas"); }
        void onEnable() {
            fancy = mc.gameSettings.fancyGraphics;
            ao = mc.gameSettings.ambientOcclusion;
            shadows = mc.gameSettings.entityShadows;
            part = mc.gameSettings.particleSetting;
            mc.gameSettings.fancyGraphics = false;
            mc.gameSettings.ambientOcclusion = 0;
            mc.gameSettings.entityShadows = false;
            mc.gameSettings.particleSetting = 2;
            if (mc.theWorld != null && mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
        }
        void onDisable() {
            mc.gameSettings.fancyGraphics = fancy;
            mc.gameSettings.ambientOcclusion = ao;
            mc.gameSettings.entityShadows = shadows;
            mc.gameSettings.particleSetting = part;
            if (mc.theWorld != null && mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
        }
    }

    // ------------------------------------------------------------------- GUI

    static class ClickGui extends GuiScreen {
        int px, py, pw, ph, scroll, maxScroll;
        int pressX, pressY, scrollStart;
        boolean dragging, pressed, focus;
        String search = "";
        Feature open;
        Setting slider;
        int sx0, sw;

        public void initGui() {
            px = (int) (width * 0.17f) + 14;
            pw = (int) (width * 0.66f);
            py = (int) (height * 0.15f);
            ph = (int) (height * 0.70f);
        }

        public boolean doesGuiPauseGame() { return false; }

        int cx() { return px + 10; }
        int cy() { return py + 26; }
        int cw() { return pw - 20; }
        int ch() { return ph - 32; }
        int cellW() { return (cw() - 16) / 3; }
        int rowY(int i) { return cy() + 14 + i * 20 - scroll; }

        List<Feature> visible() {
            List<Feature> v = new ArrayList<Feature>();
            String q = search.toLowerCase();
            for (Feature f : features) if (q.length() == 0 || f.name.toLowerCase().contains(q)) v.add(f);
            return v;
        }

        void scissor(int x, int y, int w, int h) {
            ScaledResolution sr = new ScaledResolution(mc);
            int f = sr.getScaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x * f, (sr.getScaledHeight() - y - h) * f, w * f, h * f);
        }

        void clampScroll() {
            if (scroll > maxScroll) scroll = maxScroll;
            if (scroll < 0) scroll = 0;
        }

        void checkbox(int x, int y, boolean on, String label) {
            int c = 0xFFFFFFFF;
            drawRect(x, y + 3, x + 10, y + 4, c);
            drawRect(x, y + 12, x + 10, y + 13, c);
            drawRect(x, y + 3, x + 1, y + 13, c);
            drawRect(x + 9, y + 3, x + 10, y + 13, c);
            if (on) drawRect(x + 2, y + 5, x + 8, y + 11, c);
            fontRendererObj.drawStringWithShadow(label, x + 16, y + 4, 0xFFFFFFFF);
        }

        public void drawScreen(int mx, int my, float pt) {
            drawRect(0, 0, width, height, 0x66000000);
            drawRect(px - 26, py, px - 4, py + ph, 0xDD0E0E0E);
            drawRect(px, py, px + pw, py + ph, 0xDD141414);
            drawCenteredString(fontRendererObj, "X", px - 15, py + 8, 0xFFFFFFFF);
            drawCenteredString(fontRendererObj, "H", px - 15, py + 28, 0xFFFFFFFF);
            drawCenteredString(fontRendererObj, "E", px - 15, py + ph - 20, 0xFFFFFFFF);
            fontRendererObj.drawStringWithShadow(open == null ? "Winclient" : open.name, px + 10, py + 8, 0xFFFFFFFF);

            sw = (int) (cw() * 0.35f);
            sx0 = cx() + (int) (cw() * 0.45f);

            if (open == null) {
                int bx = px + pw - 120, by = py + 5;
                drawRect(bx, by, bx + 112, by + 14, focus ? 0x55FFFFFF : 0x22FFFFFF);
                String t = search.length() == 0 && !focus ? "Search..." : search + (focus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
                fontRendererObj.drawString(t, bx + 4, by + 3, 0xFFFFFFFF);

                List<Feature> v = visible();
                int rows = (v.size() + 2) / 3;
                maxScroll = Math.max(0, rows * 30 - ch());
                clampScroll();
                scissor(cx(), cy(), cw(), ch());
                for (int i = 0; i < v.size(); i++) {
                    Feature f = v.get(i);
                    int x = cx() + (i % 3) * (cellW() + 8);
                    int y = cy() + (i / 3) * 30 - scroll;
                    int col = f.enabled ? 0xFFFFFFFF : 0xFF7A7A7A;
                    drawRect(x, y, x + cellW(), y + 22, 0x55000000);
                    drawRect(x, y, x + cellW(), y + 1, col);
                    drawRect(x, y + 21, x + cellW(), y + 22, col);
                    drawRect(x, y, x + 1, y + 22, col);
                    drawRect(x + cellW() - 1, y, x + cellW(), y + 22, col);
                    fontRendererObj.drawString(f.name, x + 8, y + 7, col);
                    fontRendererObj.drawString("...", x + cellW() - 18, y + 7, col);
                }
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            } else {
                int n = open.settings.size() + 1;
                maxScroll = Math.max(0, n * 20 + 14 - ch());
                clampScroll();
                scissor(cx(), cy(), cw(), ch());
                fontRendererObj.drawString(open.desc, cx() + 4, cy() - scroll, 0xFF999999);
                for (int i = 0; i < n; i++) {
                    int y = rowY(i);
                    if (i == 0) { checkbox(cx() + 4, y, open.enabled, "Ativado"); continue; }
                    Setting s = open.settings.get(i - 1);
                    if (s.isBool) {
                        checkbox(cx() + 4, y, s.b, s.name);
                    } else {
                        fontRendererObj.drawStringWithShadow(s.name, cx() + 4, y + 4, 0xFFFFFFFF);
                        drawRect(sx0, y + 7, sx0 + sw, y + 9, 0xFF666666);
                        float p = (s.v - s.min) / (s.max - s.min);
                        int hx = sx0 + (int) (p * sw);
                        drawRect(hx - 2, y + 3, hx + 2, y + 13, 0xFFFFFFFF);
                        String vs = (s.max - s.min) >= 10f ? String.valueOf((int) s.v) : String.format("%.1f", s.v);
                        fontRendererObj.drawStringWithShadow(vs, sx0 + sw + 8, y + 4, 0xFFFFFFFF);
                    }
                }
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }

        void updateSlider(int mx) {
            if (slider == null) return;
            float p = (mx - sx0) / (float) sw;
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            float v = slider.min + p * (slider.max - slider.min);
            if (slider.max - slider.min >= 10f) v = Math.round(v); else v = Math.round(v * 10f) / 10f;
            slider.v = v;
        }

        protected void mouseClicked(int mx, int my, int btn) throws IOException {
            pressed = true; dragging = false;
            pressX = mx; pressY = my; scrollStart = scroll;
            if (open != null) {
                for (int i = 1; i <= open.settings.size(); i++) {
                    Setting s = open.settings.get(i - 1);
                    int y = rowY(i);
                    if (!s.isBool && mx >= sx0 - 4 && mx <= sx0 + sw + 4 && my >= y && my <= y + 16
                            && my >= cy() && my <= cy() + ch()) {
                        slider = s; updateSlider(mx); return;
                    }
                }
            }
        }

        protected void mouseClickMove(int mx, int my, int btn, long t) {
            if (slider != null) { updateSlider(mx); return; }
            if (!pressed) return;
            if (Math.abs(my - pressY) > 3) dragging = true;
            if (dragging) { scroll = scrollStart - (my - pressY); clampScroll(); }
        }

        protected void mouseReleased(int mx, int my, int state) {
            if (slider != null) { slider = null; pressed = false; return; }
            if (pressed && !dragging) click(mx, my);
            pressed = false; dragging = false;
        }

        void click(int mx, int my) {
            if (mx >= px - 26 && mx <= px - 4) {
                if (my < py + 22) mc.displayGuiScreen(null);
                else if (my < py + 42) { open = null; scroll = 0; }
                else if (my > py + ph - 34) mc.displayGuiScreen(new HudEditor());
                return;
            }
            if (open == null) {
                int bx = px + pw - 120, by = py + 5;
                focus = mx >= bx && mx <= bx + 112 && my >= by && my <= by + 14;
                if (focus) return;
                if (my < cy() || my > cy() + ch() || mx < cx()) return;
                int col = (mx - cx()) / (cellW() + 8);
                if ((mx - cx()) % (cellW() + 8) > cellW() || col > 2) return;
                int row = (my - cy() + scroll) / 30;
                if ((my - cy() + scroll) % 30 > 22) return;
                int idx = row * 3 + col;
                List<Feature> v = visible();
                if (idx < 0 || idx >= v.size()) return;
                int cellX = cx() + col * (cellW() + 8);
                if (mx > cellX + cellW() - 24) { open = v.get(idx); scroll = 0; }
                else v.get(idx).toggle();
            } else {
                for (int i = 0; i <= open.settings.size(); i++) {
                    int y = rowY(i);
                    if (my >= y && my <= y + 16 && my >= cy() && my <= cy() + ch() && mx >= cx() && mx <= cx() + cw()) {
                        if (i == 0) open.toggle();
                        else {
                            Setting s = open.settings.get(i - 1);
                            if (s.isBool) s.b = !s.b;
                        }
                        return;
                    }
                }
            }
        }

        public void handleMouseInput() throws IOException {
            super.handleMouseInput();
            int d = Mouse.getEventDWheel();
            if (d != 0) { scroll += d > 0 ? -20 : 20; clampScroll(); }
        }

        protected void keyTyped(char c, int k) throws IOException {
            if (focus && open == null) {
                if (k == Keyboard.KEY_ESCAPE || k == Keyboard.KEY_RETURN) { focus = false; return; }
                if (k == Keyboard.KEY_BACK) {
                    if (search.length() > 0) search = search.substring(0, search.length() - 1);
                } else if (c >= 32 && c != 127 && search.length() < 20) {
                    search += c;
                }
                scroll = 0;
                return;
            }
            if (k == Keyboard.KEY_ESCAPE || k == Keyboard.KEY_RSHIFT) mc.displayGuiScreen(null);
        }

        public void onGuiClosed() { save(); }
    }

    static class HudEditor extends GuiScreen {
        HudFeature drag;
        int ox, oy;

        public boolean doesGuiPauseGame() { return false; }

        public void drawScreen(int mx, int my, float pt) {
            drawRect(0, 0, width, height, 0x44000000);
            drawCenteredString(fontRendererObj, "Arraste os HUDs. ESC para sair.", width / 2, 6, 0xFFFFFFFF);
            for (Feature f : features) {
                if (f.enabled && f instanceof HudFeature) {
                    HudFeature h = (HudFeature) f;
                    int c = 0x99FFFFFF;
                    drawRect(h.x - 1, h.y - 1, h.x + h.w + 1, h.y, c);
                    drawRect(h.x - 1, h.y + h.h, h.x + h.w + 1, h.y + h.h + 1, c);
                    drawRect(h.x - 1, h.y, h.x, h.y + h.h, c);
                    drawRect(h.x + h.w, h.y, h.x + h.w + 1, h.y + h.h, c);
                }
            }
        }

        protected void mouseClicked(int mx, int my, int btn) throws IOException {
            for (int i = features.size() - 1; i >= 0; i--) {
                Feature f = features.get(i);
                if (f.enabled && f instanceof HudFeature) {
                    HudFeature h = (HudFeature) f;
                    if (mx >= h.x && mx <= h.x + h.w && my >= h.y && my <= h.y + h.h) {
                        drag = h; ox = mx - h.x; oy = my - h.y; return;
                    }
                }
            }
        }

        protected void mouseClickMove(int mx, int my, int btn, long t) {
            if (drag == null) return;
            drag.x = Math.max(0, Math.min(width - drag.w, mx - ox));
            drag.y = Math.max(0, Math.min(height - drag.h, my - oy));
        }

        protected void mouseReleased(int mx, int my, int state) { drag = null; }

        public void onGuiClosed() { save(); }
    }
}
