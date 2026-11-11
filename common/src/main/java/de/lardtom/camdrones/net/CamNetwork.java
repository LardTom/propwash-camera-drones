package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CamNetwork {
    public static final int PROTOCOL = 1;
    public static final String VERSION = Integer.toString(PROTOCOL);

    public interface ServerHandler<T extends CustomPacketPayload> {
        void handle(T payload, ServerPlayer player);
    }

    public interface ClientHandler<T extends CustomPacketPayload> {
        void handle(T payload);
    }

    public interface ServerSender {
        boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

        void send(ServerPlayer player, CustomPacketPayload payload);
    }

    public interface ClientSender {
        boolean canSend(CustomPacketPayload.Type<?> type);

        void send(CustomPacketPayload payload);
    }

    public record ServerBound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                             StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                             ServerHandler<T> handler) {
    }

    public record ClientBound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                             StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
    }

    private static final List<ServerBound<?>> SERVER_BOUND = new ArrayList<>();
    private static final List<ClientBound<?>> CLIENT_BOUND = new ArrayList<>();
    private static final Set<CustomPacketPayload.Type<?>> TYPES = new HashSet<>();
    private static final Map<CustomPacketPayload.Type<?>, ClientHandler<?>> CLIENT_HANDLERS = new ConcurrentHashMap<>();
    private static boolean defined;
    private static boolean frozen;
    private static volatile ServerSender serverSender;
    private static volatile ClientSender clientSender;

    private CamNetwork() {
    }

    public static synchronized void define() {
        if (defined) {
            return;
        }
        defined = true;
        MissionUpload.define();
        PlannerSave.define();
        MissionCommand.define();
        MissionProgress.define();
        SmartMode.define();
        FollowConsent.define();
        StatusMessage.define();
        CrewPackets.define();
        ExitSettingUpload.define();
        ViewSettingUpload.define();
        VideoRecorded.define();
    }

    public static synchronized <T extends CustomPacketPayload> void serverBound(CustomPacketPayload.Type<T> type,
                                                                                StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                                ServerHandler<T> handler) {
        claim(type);
        SERVER_BOUND.add(new ServerBound<>(type, Objects.requireNonNull(codec, "codec"), Objects.requireNonNull(handler, "handler")));
    }

    public static synchronized <T extends CustomPacketPayload> void clientBound(CustomPacketPayload.Type<T> type,
                                                                                StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        claim(type);
        CLIENT_BOUND.add(new ClientBound<>(type, Objects.requireNonNull(codec, "codec")));
    }

    private static void claim(CustomPacketPayload.Type<?> type) {
        Objects.requireNonNull(type, "type");
        if (frozen) {
            throw new IllegalStateException("Pakete von " + CamDrones.MOD_ID + " sind schon beim Loader angemeldet: " + type.id());
        }
        if (!CamDrones.MOD_ID.equals(type.id().getNamespace())) {
            throw new IllegalArgumentException("Paket außerhalb des Namensraums " + CamDrones.MOD_ID + ": " + type.id());
        }
        if (!TYPES.add(type)) {
            throw new IllegalArgumentException("Paket doppelt angemeldet: " + type.id());
        }
    }

    public static synchronized List<ServerBound<?>> serverBound() {
        define();
        frozen = true;
        return List.copyOf(SERVER_BOUND);
    }

    public static synchronized List<ClientBound<?>> clientBound() {
        define();
        frozen = true;
        return List.copyOf(CLIENT_BOUND);
    }

    public static <T extends CustomPacketPayload> void onClient(CustomPacketPayload.Type<T> type, ClientHandler<T> handler) {
        CLIENT_HANDLERS.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(handler, "handler"));
    }

    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> void dispatchClient(T payload) {
        ClientHandler<T> handler = (ClientHandler<T>) CLIENT_HANDLERS.get(payload.type());
        if (handler != null) {
            handler.handle(payload);
        }
    }

    public static void bindServer(ServerSender sender) {
        serverSender = Objects.requireNonNull(sender, "sender");
    }

    public static void bindClient(ClientSender sender) {
        clientSender = Objects.requireNonNull(sender, "sender");
    }

    public static boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        ClientSender sender = clientSender;
        return sender != null && sender.canSend(type);
    }

    public static boolean sendToServer(CustomPacketPayload payload) {
        ClientSender sender = clientSender;
        if (sender == null || !sender.canSend(payload.type())) {
            return false;
        }
        sender.send(payload);
        return true;
    }

    public static boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerSender sender = serverSender;
        if (sender == null || !sender.canSend(player, payload.type())) {
            return false;
        }
        sender.send(player, payload);
        return true;
    }
}
