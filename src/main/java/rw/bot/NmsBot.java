package rw.bot;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;

final class NmsBot {

    private final MinecraftServer server;
    private final ServerLevel level;
    private final ServerPlayer player;
    private final CraftPlayer bukkit;
    private final EmbeddedChannel channel;
    private final Random random = new Random();
    private long nextAction = 0;

    private NmsBot(MinecraftServer server, ServerLevel level, ServerPlayer player, EmbeddedChannel channel) {
        this.server = server;
        this.level = level;
        this.player = player;
        this.bukkit = player.getBukkitEntity();
        this.channel = channel;
    }

    static NmsBot spawn(String name, Location loc) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());

        SilentConnection connection = new SilentConnection();
        EmbeddedChannel channel = new EmbeddedChannel(new Discard(), connection);

        server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));

        NmsBot bot = new NmsBot(server, level, player, channel);
        bot.bukkit.teleport(loc);
        player.setYHeadRot(loc.getYaw());
        player.setInvulnerable(true);
        bot.bukkit.setSleepingIgnored(true);
        return bot;
    }

    boolean isOnline() {
        return bukkit.isOnline();
    }

    void tick(long now) {
        if (now % 10 == 0) {
            player.connection.resetPosition();
            level.getChunkSource().move(player);
        }
        player.doTick();

        if (now >= nextAction) {
            float yaw = player.getYRot() + (random.nextFloat() * 120f - 60f);
            float pitch = random.nextFloat() * 45f - 20f;
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.setYHeadRot(yaw);
            if (random.nextInt(5) == 0) {
                player.swing(InteractionHand.MAIN_HAND);
            }
            nextAction = now + 100 + random.nextInt(500);
        }
    }

    void remove() {
        try {
            if (bukkit.isOnline()) {
                server.getPlayerList().remove(player);
            }
        } finally {
            channel.close();
        }
    }

    private static final class SilentConnection extends Connection {
        SilentConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void setReadOnly() {
        }

        @Override
        public void handleDisconnection() {
        }

        @Override
        public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> info, T listener) {
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        }
    }

    private static final class Discard extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ReferenceCountUtil.release(msg);
            promise.setSuccess();
        }
    }
}
