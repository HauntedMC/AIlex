package nl.hauntedmc.ailex.assistant.adapter.paper;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.ChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.assistant.chat.AssistantChatController;
import nl.hauntedmc.ailex.assistant.chat.AssistantChatObservationService;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class AssistantChatListenerTest {

    private final AIlexPlugin plugin = mock(AIlexPlugin.class);
    private final Server server = mock(Server.class);
    private final Logger logger = mock(Logger.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final AssistantChatController controller = mock(AssistantChatController.class);
    private final AssistantChatObservationService observation = mock(AssistantChatObservationService.class);
    private final Player source = player();
    private final Player other = player();
    private final Listener synchronousListener = mock(Listener.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final AssistantChatListener listener = new AssistantChatListener(plugin, controller, observation);
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        doReturn(List.of(source, other)).when(server).getOnlinePlayers();
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            queued.add(invocation.getArgument(1));
            return mock(BukkitTask.class);
        });
    }

    @AfterEach
    void tearDown() {
        ChatEvent.getHandlerList().unregister(synchronousListener);
        bukkit.close();
    }

    @ParameterizedTest
    @EnumSource(value = EventPriority.class, names = {"LOWEST", "LOW", "NORMAL", "HIGH", "HIGHEST"})
    void cancellationAtAnyMutatingPriorityPreventsIngestionRegardlessOfRegistrationOrder(EventPriority priority)
            throws Exception {
        EventHandler annotation = AssistantChatListener.class.getMethod("onChat", AsyncChatEvent.class)
                .getAnnotation(EventHandler.class);
        assertEquals(EventPriority.MONITOR, annotation.priority());
        for (boolean async : List.of(false, true)) {
            for (boolean registerAssistantFirst : List.of(false, true)) {
                HandlerList dispatch = new HandlerList();
                RegisteredListener assistant = new RegisteredListener(listener,
                        (ignored, event) -> listener.onChat((AsyncChatEvent) event),
                        annotation.priority(), plugin, annotation.ignoreCancelled());
                RegisteredListener canceller = new RegisteredListener(mock(Listener.class),
                        (ignored, event) -> ((AsyncChatEvent) event).setCancelled(true), priority, plugin, false);
                try {
                    dispatch.register(registerAssistantFirst ? assistant : canceller);
                    dispatch.register(registerAssistantFirst ? canceller : assistant);
                    AsyncChatEvent event = event(async, source, other);
                    for (RegisteredListener registered : dispatch.getRegisteredListeners()) {
                        registered.callEvent(event);
                    }
                } finally {
                    dispatch.unregister(listener);
                    dispatch.unregister(canceller.getListener());
                }
            }
        }
        assertEquals(0, queued.size());
        verifyNoInteractions(observation, controller);
    }

    @Test
    void explicitGuardAlsoRejectsDirectCallsWithCancelledEvents() {
        for (boolean async : List.of(false, true)) {
            AsyncChatEvent event = event(async, source, other);
            event.setCancelled(true);
            listener.onChat(event);
        }
        assertEquals(0, queued.size());
        verifyNoInteractions(observation, controller);
    }

    @Test
    void publicChatUsesModifiedSnapshotExactlyOnceInOrderOnServerTask() {
        AsyncChatEvent event = event(true, source, other);
        Component modified = Component.text("public redacted message");
        event.message(modified);
        listener.onChat(event);
        verifyNoInteractions(observation, controller);
        assertEquals(1, queued.size());

        // Services receive only the immutable MONITOR snapshot, never a later payload or mutable viewer set.
        event.message(Component.text("later payload"));
        event.viewers().clear();
        queued.getFirst().run();
        var order = inOrder(observation, controller);
        order.verify(observation).observe(source, modified);
        order.verify(controller).handleChat(source, modified);
        order.verifyNoMoreInteractions();
    }

    @Test
    void synchronousPublicChatUsesTheSameGateWithoutScheduling() {
        AsyncChatEvent event = event(false, source, other, mock(ConsoleCommandSender.class));
        listener.onChat(event);
        var order = inOrder(observation, controller);
        order.verify(observation).observe(source, event.message());
        order.verify(controller).handleChat(source, event.message());
        order.verifyNoMoreInteractions();
        assertEquals(0, queued.size());
    }

    @Test
    void rejectsRestrictedEmptyConsoleOnlyAndOpaqueAudiencesInBothPaths() {
        for (boolean async : List.of(false, true)) {
            listener.onChat(event(async, source));
            listener.onChat(event(async, other));
            listener.onChat(event(async));
            listener.onChat(event(async, mock(ConsoleCommandSender.class)));
            listener.onChat(event(async, source, other, mock(Audience.class)));
        }
        queued.forEach(Runnable::run);
        verifyNoInteractions(observation, controller);
        verifyNoInteractions(logger);
    }

    @Test
    void cancellationDuringHandoffPreventsAllConsumers() {
        AsyncChatEvent event = event(true, source, other);
        listener.onChat(event);
        event.setCancelled(true);
        queued.getFirst().run();
        verifyNoInteractions(observation, controller);
    }

    @Test
    void joiningPlayerAbsentFromSnapshotPreventsIngestion() {
        listener.onChat(event(true, source, other));
        doReturn(List.of(source, other, player())).when(server).getOnlinePlayers();
        queued.getFirst().run();
        verifyNoInteractions(observation, controller);
    }

    @Test
    void disconnectedSenderPreventsIngestion() {
        listener.onChat(event(true, source, other));
        when(source.isOnline()).thenReturn(false);
        queued.getFirst().run();
        verifyNoInteractions(observation, controller);
    }

    @Test
    void laterSynchronousHandlersBlockWithoutReadingPayloadAndRecoveryIsLoggedOnce() {
        registerSynchronousHandler();
        AsyncChatEvent blocked = mock(AsyncChatEvent.class);
        listener.onChat(blocked);
        listener.onChat(blocked);
        verify(blocked, never()).message();
        verify(blocked, never()).viewers();
        assertEquals(0, queued.size());
        verifyNoInteractions(observation, controller);
        verify(logger, times(1)).warning(anyString());

        ChatEvent.getHandlerList().unregister(synchronousListener);
        listener.onChat(event(false, source, other));
        listener.onChat(event(false, source, other));
        verify(logger, times(1)).info(anyString());
        verify(observation, times(2)).observe(eq(source), any(Component.class));
        verify(controller, times(2)).handleChat(eq(source), any(Component.class));

        registerSynchronousHandler();
        listener.onChat(blocked);
        verify(logger, times(2)).warning(anyString());
    }

    @Test
    void synchronousHandlerRegisteredDuringHandoffAlsoBlocksIngestion() {
        listener.onChat(event(true, source, other));
        registerSynchronousHandler();
        queued.getFirst().run();
        verifyNoInteractions(observation, controller);
        verify(logger).warning(anyString());
    }

    private void registerSynchronousHandler() {
        ChatEvent.getHandlerList().register(new RegisteredListener(synchronousListener,
                (ignored, event) -> {}, EventPriority.NORMAL, plugin, false));
    }

    private AsyncChatEvent event(boolean async, Audience... viewers) {
        return new AsyncChatEvent(async, source, new HashSet<>(Set.of(viewers)), mock(ChatRenderer.class),
                Component.text("original private content"), Component.text("original private content"),
                mock(SignedMessage.class));
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }
}
