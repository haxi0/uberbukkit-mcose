package net.minecraft.server;

import org.bukkit.entity.Player;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class VoiceChatRoomManager {
	private final MinecraftServer server;
	private final Map<String, ChatRoom> rooms = new LinkedHashMap<String, ChatRoom>();
	private final Map<String, String> playerToRoom = new HashMap<String, String>();
	private final Map<String, Boolean> playerRoomVoiceRouting = new HashMap<String, Boolean>();
	private final File storageDir;
	private final File roomsFile;

	public VoiceChatRoomManager(MinecraftServer server) {
		this.server = server;
		this.storageDir = server.a("chatrooms");
		if(!this.storageDir.exists()) {
			this.storageDir.mkdirs();
		}
		this.roomsFile = new File(this.storageDir, "rooms.dat");
		this.loadRooms();
	}

	public synchronized void removePlayer(EntityPlayer player) {
		if(player == null) return;
		String key = player.name.toLowerCase(Locale.ROOT);
		String roomName = this.playerToRoom.remove(key);
		// Preserve per-player voice route preference across reconnects/respawns.
		// This avoids silent fallback to default room routing after entity/session churn.
		if(roomName != null) {
			ChatRoom room = this.rooms.get(roomName);
			if(room != null) {
				room.members.remove(key);
			}
			broadcastSnapshot();
		}
	}

	public synchronized ChatRoom getRoomForPlayer(EntityPlayer player) {
		if(player == null) return null;
		String roomName = this.playerToRoom.get(player.name.toLowerCase(Locale.ROOT));
		return roomName != null ? this.rooms.get(roomName) : null;
	}

	public synchronized boolean hasVoiceRoom(EntityPlayer player) {
		return getRoomForPlayer(player) != null;
	}

	public synchronized boolean shouldRouteVoiceToRoom(EntityPlayer player) {
		if(!hasVoiceRoom(player) || player == null) {
			return false;
		}
		String key = player.name.toLowerCase(Locale.ROOT);
		Boolean routeToRoom = this.playerRoomVoiceRouting.get(key);
		return routeToRoom == null || routeToRoom.booleanValue();
	}

	public synchronized List<EntityPlayer> getVoiceRoomRecipients(EntityPlayer speaker) {
		ChatRoom room = getRoomForPlayer(speaker);
		if (room == null) {
			return Collections.emptyList();
		}
		List<EntityPlayer> recipients = new ArrayList<EntityPlayer>();
		List<EntityPlayer> onlinePlayers = this.server.serverConfigurationManager.getOnlinePlayersSnapshot();
		for (int i = 0; i < onlinePlayers.size(); i++) {
			EntityPlayer member = onlinePlayers.get(i);
			if (speaker != null && member == speaker) {
				continue;
			}
			if (room.members.contains(member.name.toLowerCase(Locale.ROOT))) {
				recipients.add(member);
			}
		}
		return recipients;
	}

	public synchronized void handleAction(EntityPlayer player, Packet66ChatRoomAction packet) {
		if(player == null || packet == null) {
			return;
		}
		switch(packet.action) {
			case Packet66ChatRoomAction.ACTION_CREATE:
				this.createRoom(player, packet.roomName);
				break;
			case Packet66ChatRoomAction.ACTION_JOIN:
				this.joinRoom(player, packet.roomName);
				break;
			case Packet66ChatRoomAction.ACTION_LEAVE:
				this.leaveRoom(player);
				break;
			case Packet66ChatRoomAction.ACTION_DELETE:
				this.deleteRoom(player, packet.roomName);
				break;
			case Packet66ChatRoomAction.ACTION_REFRESH:
				this.sendSnapshot(player);
				break;
			case Packet66ChatRoomAction.ACTION_TOGGLE_PIN:
				this.togglePin(player, packet.roomName);
				break;
			case Packet66ChatRoomAction.ACTION_KICK:
				this.kickMember(player, packet.roomName, packet.targetName);
				break;
			case Packet66ChatRoomAction.ACTION_SET_VOICE_ROUTE:
				this.setVoiceRoutePreference(player, packet.targetName);
				break;
			default:
				break;
		}
	}

	private void setVoiceRoutePreference(EntityPlayer player, String route) {
		if(player == null) {
			return;
		}
		String normalized = route != null ? route.trim().toLowerCase(Locale.ROOT) : "";
		boolean routeToRoom;
		if("room".equals(normalized) || "chatroom".equals(normalized)) {
			routeToRoom = true;
		} else if("proximity".equals(normalized) || "local".equals(normalized)) {
			routeToRoom = false;
		} else {
			return;
		}
		this.playerRoomVoiceRouting.put(player.name.toLowerCase(Locale.ROOT), Boolean.valueOf(routeToRoom));
		sendMessage(player, routeToRoom ? "\u00A77Voice route set to chat room." : "\u00A77Voice route set to proximity.");
	}

	private boolean canCreate(EntityPlayer player) {
		return hasPermission(getBukkitPlayer(player), "uberbukkit.chatroom.create");
	}

	private boolean canKick(EntityPlayer player) {
		return hasPermission(getBukkitPlayer(player), "uberbukkit.chatroom.kick");
	}

	private boolean hasPermission(Player bukkit, String node) {
		if(bukkit == null) {
			return false;
		}
		return bukkit.isOp() || bukkit.hasPermission(node) || bukkit.hasPermission("uberbukkit.*");
	}

	private void createRoom(EntityPlayer player, String requestedName) {
		String sanitized = sanitizeRoomName(requestedName);
		if(sanitized.length() < 3) {
			sendMessage(player, "\u00A7cRoom name must be at least 3 characters.");
			return;
		}
		if(!canCreate(player)) {
			sendMessage(player, "\u00A7cYou do not have permission to create chat rooms.");
			return;
		}
		String key = sanitized.toLowerCase(Locale.ROOT);
		if(this.rooms.containsKey(key)) {
			sendMessage(player, "\u00A7cA room with that name already exists.");
			return;
		}
		ChatRoom room = new ChatRoom(sanitized, player.name);
		this.rooms.put(key, room);
			sendMessage(player, "\u00A7aCreated chat room '" + sanitized + "'.");
		this.joinRoomInternal(player, key);
		broadcastSnapshot();
		saveRooms();
	}

	private void joinRoom(EntityPlayer player, String roomName) {
		String key = roomName != null ? roomName.toLowerCase(Locale.ROOT) : "";
		ChatRoom room = this.rooms.get(key);
		if(room == null) {
			sendMessage(player, "\u00A7cChat room not found.");
			return;
		}
		if(this.playerToRoom.containsKey(player.name.toLowerCase(Locale.ROOT)) && this.playerToRoom.get(player.name.toLowerCase(Locale.ROOT)).equals(key)) {
			sendMessage(player, "\u00A7eYou are already in that room.");
			return;
		}
		this.joinRoomInternal(player, key);
		sendMessage(player, "\u00A7aJoined chat room '" + room.name + "'.");
		broadcastSnapshot();
	}

	private void joinRoomInternal(EntityPlayer player, String roomKey) {
		this.leaveRoom(player);
		ChatRoom room = this.rooms.get(roomKey);
		if(room != null) {
			String playerKey = player.name.toLowerCase(Locale.ROOT);
			room.members.add(playerKey);
			this.playerToRoom.put(playerKey, roomKey);
		}
	}

	private void leaveRoom(EntityPlayer player) {
		if(player == null) return;
		String playerKey = player.name.toLowerCase(Locale.ROOT);
		String roomName = this.playerToRoom.remove(playerKey);
		if(roomName != null) {
			ChatRoom room = this.rooms.get(roomName);
			if(room != null) {
				room.members.remove(playerKey);
				sendMessage(player, "\u00A7aLeft chat room '" + room.name + "'.");
			}
			broadcastSnapshot();
			saveRooms();
		}
	}

	private void togglePin(EntityPlayer player, String roomName) {
		String key = roomName != null ? roomName.toLowerCase(Locale.ROOT) : "";
		ChatRoom room = this.rooms.get(key);
		if(room == null) {
			sendMessage(player, "\u00A7cChat room not found.");
			return;
		}
		if(!canCreate(player)) {
			sendMessage(player, "\u00A7cYou do not have permission to pin chat rooms.");
			return;
		}
		room.pinned = !room.pinned;
		sendMessage(player, room.pinned ? "\u00A7aPinned chat room '" + room.name + "'." : "\u00A7aUnpinned chat room '" + room.name + "'.");
		broadcastSnapshot();
		saveRooms();
	}

	private void deleteRoom(EntityPlayer player, String roomName) {
		String key = roomName != null ? roomName.toLowerCase(Locale.ROOT) : "";
		ChatRoom room = this.rooms.get(key);
		if(room == null) {
			sendMessage(player, "\u00A7cChat room not found.");
			return;
		}
		boolean canDelete = room.owner.equalsIgnoreCase(player.name) || canCreate(player);
		if(!canDelete) {
			sendMessage(player, "\u00A7cYou cannot delete that room.");
			return;
		}
		this.rooms.remove(key);
		Iterator<Map.Entry<String, String>> it = this.playerToRoom.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<String, String> entry = it.next();
			if(entry.getValue().equals(key)) {
				it.remove();
				EntityPlayer member = this.server.serverConfigurationManager.i(entry.getKey());
				if(member != null) {
					sendMessage(member, "\u00A7cChat room '" + room.name + "' was deleted.");
				}
			}
		}
		sendMessage(player, "\u00A7aDeleted chat room '" + room.name + "'.");
		broadcastSnapshot();
		saveRooms();
	}

	private void kickMember(EntityPlayer moderator, String roomName, String targetName) {
		if(moderator == null) return;
		if(targetName == null || targetName.trim().length() == 0) {
			sendMessage(moderator, "\u00A7cNo player selected.");
			return;
		}
		String key = roomName != null ? roomName.toLowerCase(Locale.ROOT) : "";
		ChatRoom room = this.rooms.get(key);
		if(room == null) {
			sendMessage(moderator, "\u00A7cChat room not found.");
			return;
		}
		boolean isOwner = room.owner.equalsIgnoreCase(moderator.name);
		if(!isOwner && !canKick(moderator)) {
			sendMessage(moderator, "\u00A7cYou cannot manage that room.");
			return;
		}
		String targetKey = targetName.toLowerCase(Locale.ROOT);
		if(room.owner.equalsIgnoreCase(targetName)) {
			sendMessage(moderator, "\u00A7cYou cannot remove the room owner.");
			return;
		}
		if(!room.members.remove(targetKey)) {
			sendMessage(moderator, "\u00A7cPlayer '" + targetName + "' is not in that room.");
			return;
		}
		this.playerToRoom.remove(targetKey);
		EntityPlayer kicked = this.server.serverConfigurationManager.i(targetName);
		if(kicked != null) {
			sendMessage(kicked, "\u00A7cYou were removed from chat room '" + room.name + "'.");
		}
		sendMessage(moderator, "\u00A7aRemoved " + targetName + " from chat room '" + room.name + "'.");
		broadcastSnapshot();
	}

	private void loadRooms() {
		this.rooms.clear();
		this.playerToRoom.clear();
		if(this.roomsFile == null || !this.roomsFile.exists()) {
			return;
		}
		DataInputStream in = null;
		try {
			in = new DataInputStream(new BufferedInputStream(new FileInputStream(this.roomsFile)));
			int count = in.readInt();
			for(int i = 0; i < count; ++i) {
				String name = in.readUTF();
				String owner = in.readUTF();
				boolean pinned = in.readBoolean();
				String key = name.toLowerCase(Locale.ROOT);
				ChatRoom room = new ChatRoom(name, owner);
				room.pinned = pinned;
				this.rooms.put(key, room);
			}
		} catch (IOException ex) {
			System.err.println("[VoiceChatRoomManager] Failed to load chat rooms: " + ex.getMessage());
		} finally {
			if(in != null) {
				try { in.close(); } catch (IOException ignored) {}
			}
		}
	}

	private void saveRooms() {
		if(this.roomsFile == null) {
			return;
		}
		DataOutputStream out = null;
		try {
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(this.roomsFile)));
			out.writeInt(this.rooms.size());
			for(ChatRoom room : this.rooms.values()) {
				out.writeUTF(room.name);
				out.writeUTF(room.owner);
				out.writeBoolean(room.pinned);
			}
		} catch (IOException ex) {
			System.err.println("[VoiceChatRoomManager] Failed to save chat rooms: " + ex.getMessage());
		} finally {
			if(out != null) {
				try { out.close(); } catch (IOException ignored) {}
			}
		}
	}

	private String sanitizeRoomName(String input) {
		if(input == null) return "";
		String trimmed = input.trim();
		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < trimmed.length() && builder.length() < 32; ++i) {
			char c = trimmed.charAt(i);
			if(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ') {
				builder.append(c);
			}
		}
		return builder.toString().trim();
	}

	private Player getBukkitPlayer(EntityPlayer player) {
		if(player == null) return null;
		try {
			return (Player) player.getBukkitEntity();
		} catch (Throwable ignored) {
			return null;
		}
	}

	private void sendMessage(EntityPlayer player, String message) {
		Player bukkit = getBukkitPlayer(player);
		if(bukkit != null) {
			bukkit.sendMessage(message);
		}
	}

	public synchronized void broadcastSnapshot() {
		List<EntityPlayer> onlinePlayers = this.server.serverConfigurationManager.getOnlinePlayersSnapshot();
		for (int i = 0; i < onlinePlayers.size(); i++) {
			this.sendSnapshot(onlinePlayers.get(i));
		}
	}

	public synchronized void sendSnapshot(EntityPlayer viewer) {
		if(viewer == null) return;
		viewer.netServerHandler.sendPacket(buildSnapshotFor(viewer));
	}

	public synchronized Packet65ChatRooms buildSnapshotFor(EntityPlayer viewer) {
		List<Packet65ChatRooms.ChatRoomSnapshot> list = new ArrayList<Packet65ChatRooms.ChatRoomSnapshot>();
		String viewerKey = viewer != null ? viewer.name.toLowerCase(Locale.ROOT) : "";
		String activeRoomKey = this.playerToRoom.get(viewerKey);
		for(ChatRoom room : this.rooms.values()) {
			Packet65ChatRooms.ChatRoomSnapshot snapshot = new Packet65ChatRooms.ChatRoomSnapshot();
			snapshot.name = room.name;
			snapshot.owner = room.owner;
			snapshot.members = new ArrayList<String>();
			for(String member : room.members) {
				EntityPlayer online = this.server.serverConfigurationManager.i(member);
				snapshot.members.add(online != null ? online.name : member);
			}
			snapshot.isMember = room.members.contains(viewerKey);
			snapshot.isOwner = room.owner.equalsIgnoreCase(viewer.name);
			snapshot.isPinned = room.pinned;
			list.add(snapshot);
		}
		boolean canCreate = canCreate(viewer);
		String activeRoomName = "";
		if(activeRoomKey != null) {
			ChatRoom existing = this.rooms.get(activeRoomKey);
			if(existing != null) {
				activeRoomName = existing.name;
			}
		}
		return new Packet65ChatRooms(list, canCreate, activeRoomName);
	}

	public synchronized void broadcastVoice(EntityPlayer speaker, Packet64Voice inbound) {
		ChatRoom room = getRoomForPlayer(speaker);
		if(room == null) {
			return;
		}
		Packet64Voice outbound = inbound.cloneForForwarding(speaker.id, 0.0F, speaker.name);
		List<EntityPlayer> onlinePlayers = this.server.serverConfigurationManager.getOnlinePlayersSnapshot();
		for (int i = 0; i < onlinePlayers.size(); i++) {
			EntityPlayer member = onlinePlayers.get(i);
			if(member == speaker) continue;
			if(room.members.contains(member.name.toLowerCase(Locale.ROOT))) {
				member.netServerHandler.sendPacket(outbound);
			}
		}
	}

	private static final class ChatRoom {
		private final String name;
		private final String owner;
		private final Set<String> members = new LinkedHashSet<String>();
		private boolean pinned = false;

		private ChatRoom(String name, String owner) {
			this.name = name;
			this.owner = owner;
		}
	}
}
