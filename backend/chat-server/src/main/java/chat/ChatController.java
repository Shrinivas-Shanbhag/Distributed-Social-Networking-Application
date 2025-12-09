package chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/chat")
@Component
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate rest = new RestTemplate();
    private final String DB_SERVICE_BASE;

    private final Map<String, Map<String, Long>> lastSeenTimestamp = new ConcurrentHashMap<>();
    private final Set<String> connectedUsers = ConcurrentHashMap.newKeySet();

    // Track latest follow state per user
    private final Map<String, Set<String>> userFollows = new ConcurrentHashMap<>();

    public ChatController(SimpMessagingTemplate messagingTemplate,
                          @Value("${db.service.base:http://localhost:9000}") String dbServiceBase) {
        this.messagingTemplate = messagingTemplate;
        this.DB_SERVICE_BASE = dbServiceBase;
    }

    // --- REST endpoints ---
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/follow")
    public void follow(@RequestParam String currentUser, @RequestParam String targetUser){
        Map<String,String> payload = Map.of("action","follow","currentUser",currentUser,"targetUser",targetUser);
        rest.postForEntity(DB_SERVICE_BASE + "/db/follow", payload, Void.class);

        // Update local cache immediately
        userFollows.computeIfAbsent(currentUser, k -> ConcurrentHashMap.newKeySet()).add(targetUser);

        // Broadcast follow event to client
        messagingTemplate.convertAndSend("/topic/follow-" + currentUser,
                Map.of("action","follow","targetUser",targetUser));
    }

    @PostMapping("/unfollow")
    public void unfollow(@RequestParam String currentUser, @RequestParam String targetUser){
        Map<String,String> payload = Map.of("action","unfollow","currentUser",currentUser,"targetUser",targetUser);
        rest.postForEntity(DB_SERVICE_BASE + "/db/follow", payload, Void.class);

        // Update local cache immediately
        userFollows.computeIfAbsent(currentUser, k -> ConcurrentHashMap.newKeySet()).remove(targetUser);

        // Broadcast unfollow event to client
        messagingTemplate.convertAndSend("/topic/follow-" + currentUser,
                Map.of("action","unfollow","targetUser",targetUser));
    }

    @GetMapping("/users")
    public List<Map<String,Object>> getUsers(@RequestParam String currentUser){
        ResponseEntity<List<Map<String,Object>>> r = rest.exchange(
                DB_SERVICE_BASE + "/db/users?currentUser=" + currentUser,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String,Object>>>() {}
        );
        return r.getBody();
    }

    @GetMapping("/chats")
    public Map<String,List<ChatMessage>> getChats(@RequestParam String username){
        ResponseEntity<Map<String, List<ChatMessage>>> r = rest.exchange(
                DB_SERVICE_BASE + "/db/chats?username=" + username,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, List<ChatMessage>>>() {}
        );
        return r.getBody();
    }

    @GetMapping("/timeline")
    public List<PostMessage> getTimeline(@RequestParam String currentUser){
        ResponseEntity<List<PostMessage>> r = rest.exchange(
                DB_SERVICE_BASE + "/db/timeline?currentUser=" + currentUser,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<PostMessage>>() {}
        );
        return r.getBody();
    }

    // --- WebSocket message handling ---
    @MessageMapping("/chat")
    public synchronized void sendChat(ChatMessage msg){
        rest.postForEntity(DB_SERVICE_BASE + "/db/chats", msg, Void.class);

        connectedUsers.add(msg.getFrom());
        connectedUsers.add(msg.getTo());

        lastSeenTimestamp.computeIfAbsent(msg.getFrom(), k -> new ConcurrentHashMap<>())
                .put(msg.getTo(), msg.getTimestamp());
        lastSeenTimestamp.computeIfAbsent(msg.getTo(), k -> new ConcurrentHashMap<>())
                .put(msg.getFrom(), msg.getTimestamp());

        messagingTemplate.convertAndSend("/topic/chat-"+msg.getTo(), msg);
        messagingTemplate.convertAndSend("/topic/chat-"+msg.getFrom(), msg);
    }

    @MessageMapping("/post")
    public synchronized void postTimeline(PostMessage msg){
        // Save to DB
        rest.postForEntity(DB_SERVICE_BASE + "/db/posts", msg, Void.class);

        // Fetch followers
        ResponseEntity<List<String>> r = rest.exchange(
                DB_SERVICE_BASE + "/db/followersOf?user=" + msg.getFrom(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<String>>() {}
        );

        List<String> followers = r.getBody();

        long ts = msg.getTimestamp();

        // LIVE dispatch + update timestamps so poller won't send it again
        if (followers != null) {
            for (String f : followers) {
                connectedUsers.add(f);

                lastSeenTimestamp
                        .computeIfAbsent(f, k -> new ConcurrentHashMap<>())
                        .put("__timeline__", ts);

                messagingTemplate.convertAndSend("/topic/timeline-" + f, msg);
            }
        }

        // Also update for sender (same reason)
        connectedUsers.add(msg.getFrom());

        lastSeenTimestamp
                .computeIfAbsent(msg.getFrom(), k -> new ConcurrentHashMap<>())
                .put("__timeline__", ts);

        messagingTemplate.convertAndSend("/topic/timeline-" + msg.getFrom(), msg);
    }

    private void pollTimeline() {
        for (String user : connectedUsers) {
            try {
                String url = DB_SERVICE_BASE + "/db/timeline?currentUser=" + user;
                List<Map<String,Object>> posts = rest.exchange(
                        url,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<Map<String,Object>>>() {}
                ).getBody();

                if (posts == null) continue;

                // last seen
                long lastTs = lastSeenTimestamp
                        .computeIfAbsent(user, k -> new ConcurrentHashMap<>())
                        .getOrDefault("__timeline__", 0L);

                long maxTs = lastTs;

                for (Map<String,Object> p : posts) {
                    long ts = ((Number) p.get("timestamp")).longValue();

                    if (ts > lastTs) {
                        PostMessage pm = new PostMessage(
                                (String) p.get("from"),
                                (String) p.get("text"),
                                ts
                        );

                        messagingTemplate.convertAndSend("/topic/timeline-" + user, pm);

                        // Track highest timestamp in this poll cycle
                        if (ts > maxTs) {
                            maxTs = ts;
                        }
                    }
                }

                // Update only once after all posts are processed
                lastSeenTimestamp.get(user).put("__timeline__", maxTs);

            } catch (Exception e) {
                System.err.println("Polling timeline failed for user " + user + ": " + e.getMessage());
            }
        }
    }


    // --- Polling mechanism for messages, follow/unfollow and timeline ---
    @Scheduled(fixedDelay = 3000)
    public void pollUpdates() {
        pollMessages();
        pollFollows();
        pollTimeline();
    }

    private void pollMessages() {
        for (String user : connectedUsers) {
            try {
                String url = DB_SERVICE_BASE + "/db/chats?username=" + user;
                Map<String, List<Map<String,Object>>> allChats = rest.exchange(
                        url,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, List<Map<String,Object>>>>() {}
                ).getBody();

                if (allChats != null) {
                    for (Map.Entry<String, List<Map<String,Object>>> entry : allChats.entrySet()) {
                        String peer = entry.getKey();
                        List<Map<String,Object>> messages = entry.getValue();
                        long lastTs = lastSeenTimestamp.computeIfAbsent(user, k -> new ConcurrentHashMap<>())
                                .getOrDefault(peer, 0L);

                        for (Map<String,Object> m : messages) {
                            long ts = ((Number) m.get("timestamp")).longValue();
                            if (ts > lastTs) {
                                ChatMessage cm = new ChatMessage(
                                        (String) m.get("from"),
                                        (String) m.get("to"),
                                        (String) m.get("text"),
                                        ts
                                );
                                messagingTemplate.convertAndSend("/topic/chat-"+user, cm);
                                lastSeenTimestamp.get(user).put(peer, ts);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Polling messages failed for user " + user + ": " + e.getMessage());
            }
        }
    }

    private void pollFollows() {
        for (String user : connectedUsers) {
            try {
                // fetch latest following from DB
                ResponseEntity<List<Map<String,Object>>> r = rest.exchange(
                        DB_SERVICE_BASE + "/db/users?currentUser=" + user,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<Map<String,Object>>>() {}
                );
                List<Map<String,Object>> users = r.getBody();
                if (users == null) continue;

                Set<String> currentFollow = userFollows.computeIfAbsent(user, k -> ConcurrentHashMap.newKeySet());
                for (Map<String,Object> u : users) {
                    String username = (String) u.get("username");
                    boolean followed = (Boolean) u.get("followed");
                    if (followed && !currentFollow.contains(username)) {
                        currentFollow.add(username);
                        messagingTemplate.convertAndSend("/topic/follow-" + user,
                                Map.of("action","follow","targetUser",username));
                    } else if (!followed && currentFollow.contains(username)) {
                        currentFollow.remove(username);
                        messagingTemplate.convertAndSend("/topic/follow-" + user,
                                Map.of("action","unfollow","targetUser",username));
                    }
                }
            } catch (Exception e) {
                System.err.println("Polling follows failed for user " + user + ": " + e.getMessage());
            }
        }
    }

    // --- Data classes ---
    public static class ChatMessage {
        private String from, to, text;
        private long timestamp;

        public ChatMessage(){}
        public ChatMessage(String f, String t, String txt, long ts){
            this.from = f;
            this.to = t;
            this.text = txt;
            this.timestamp = ts;
        }

        public String getFrom(){ return from; }
        public void setFrom(String f){ from = f; }
        public String getTo(){ return to; }
        public void setTo(String t){ to = t; }
        public String getText(){ return text; }
        public void setText(String t){ text = t; }
        public long getTimestamp(){ return timestamp; }
        public void setTimestamp(long ts){ timestamp = ts; }
    }

    public static class PostMessage {
        private String from, text;
        private long timestamp;

        public PostMessage(){}
        public PostMessage(String f, String t, long ts){ from = f; text = t; timestamp = ts; }

        public String getFrom(){ return from; }
        public void setFrom(String f){ from = f; }
        public String getText(){ return text; }
        public void setText(String t){ text = t; }
        public long getTimestamp(){ return timestamp; }
        public void setTimestamp(long ts){ timestamp = ts; }
    }
}
